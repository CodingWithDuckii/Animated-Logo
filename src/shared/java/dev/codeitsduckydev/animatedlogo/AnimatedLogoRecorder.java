package dev.codeitsduckydev.animatedlogo;

import dev.codeitsduckydev.animatedlogo.gui.RecordingStatusScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Exports the animated intro as an MP4 video in the user's Videos folder
 * (Videos/Animated Logo).
 *
 * The intro is deterministic: a background thread composes it from the mod's
 * twelve frame textures using the same timing the splash mixins use on
 * screen (0.7 s fade-in of the first frame, the 3.0 s logo animation over 96
 * sub-steps, then a 1.0 s fade-out to black) and feeds each rendered frame
 * straight into the MP4 encoder (JCodec, pure Java H.264). The video
 * therefore always has its full length - no screen capture is involved, so
 * the game's frame rate or disk speed cannot shorten it.
 *
 * When ffmpeg is available, the intro sound is muxed into the finished
 * video; when it is missing the recorder offers to download and install
 * it automatically (Windows), otherwise the export stays silent.
 *
 * Earlier versions captured the on-screen splash instead. That required PNG
 * frame write-out to keep up with the render thread, which it could not on a
 * busy system, so most frames were silently dropped and the exported video
 * was far too short.
 */
public final class AnimatedLogoRecorder {
    public static final int TARGET_FPS = 30;
    /** Output resolution; the logo occupies the middle half of the frame. */
    private static final int VIDEO_WIDTH = 960;
    private static final int VIDEO_HEIGHT = 540;

    // Intro timeline, mirroring the constants in the SplashOverlay mixins.
    private static final double FADE_IN_MS = 700.0;
    private static final double ANIMATION_MS = 3000.0;
    private static final double FADE_OUT_MS = 1000.0;
    private static final double TOTAL_DURATION_MS = FADE_IN_MS + ANIMATION_MS + FADE_OUT_MS;
    private static final int TOTAL_FRAMES = (int) Math.ceil(TOTAL_DURATION_MS / 1000.0 * TARGET_FPS);

    /**
     * The splash mixins start the startup sound when the fade-in of the
     * first frame finishes - exactly when the logo animation begins - so
     * the muxed audio is delayed by the same amount to stay in sync.
     */
    private static final int SOUND_START_DELAY_MS = (int) FADE_IN_MS;

    // Frame sheet layout. Every one of the twelve textures is a 1024x1024
    // ARGB sheet holding four 1024x256 logo sub-frames stacked vertically.
    private static final int SHEET_COUNT = 12;
    private static final int SHEET_WIDTH = 1024;
    private static final int SHEET_HEIGHT = 1024;
    private static final int SUBFRAME_HEIGHT = 256;
    /** Sub-steps shown per sheet (each sub-frame lasts two steps). */
    private static final int STEPS_PER_SHEET = 8;
    private static final int TOTAL_STEPS = SHEET_COUNT * STEPS_PER_SHEET;
    private static final int LAST_COUNT = TOTAL_STEPS - 1;

    /** Background colour: the vanilla splash's MOJANG_RED (239, 50, 61). */
    private static final int BRAND_RGB = 0xEF323D;

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    /** Old versions stored captured PNG frames here; still removed on boot. */
    private static final String TMP_DIR_NAME = "animated-logo-recording";

    /** Bundled splash sound, added to the finished video with ffmpeg. */
    private static final String STARTUP_SOUND_PATH = "assets/animated-mojang-logo/sounds/startup.ogg";

    private static volatile RecorderSession session;

    private AnimatedLogoRecorder() {
    }

    // ------------------------------------------------------------------
    // Hooks the splash mixins still call. The animation is exported from
    // the frame textures now, so these do nothing; they exist so the mixin
    // variants keep compiling without per-version edits.
    // ------------------------------------------------------------------

    /** Always false: the recorder no longer replays the intro on screen. */
    public static boolean isPlaybackActive() {
        return false;
    }

    /** Kept for the splash mixins; unused since recording is baked offline. */
    public static void captureFrame() {
    }

    /** Kept for the splash mixins; unused since recording is baked offline. */
    public static void onSplashAnimationFinished() {
    }

    // ------------------------------------------------------------------
    // Starting an export
    // ------------------------------------------------------------------

    /**
     * Starts a video export: shows the status screen and renders the intro
     * video in a background thread. {@code returnScreen} is shown again when
     * the user leaves the status screen afterwards.
     */
    public static void startRecording(Screen returnScreen) {
        MinecraftClient client = MinecraftClient.getInstance();

        RecorderSession s = session;
        if (s != null && !s.phase.isTerminal()) {
            showFailedScreen(client, returnScreen, "A recording is already running.");
            return;
        }

        try {
            cleanupTmpDir();
            RecorderSession r = new RecorderSession(returnScreen);
            session = r;
            client.setScreen(new RecordingStatusScreen(r));
            r.startEncodeThread();
            AnimatedLogo.LOGGER.info("Rendering animated logo intro video");
        } catch (Exception e) {
            AnimatedLogo.LOGGER.error("Failed to start intro recording", e);
            showFailedScreen(client, returnScreen, "Could not start the recording: " + e.getMessage());
        }
    }

    private static void showFailedScreen(MinecraftClient client, Screen returnScreen, String message) {
        RecorderSession failed = new RecorderSession(returnScreen);
        failed.phase = Phase.FAILED;
        failed.errorMessage = message;
        session = failed;
        client.setScreen(new RecordingStatusScreen(failed));
    }

    // ------------------------------------------------------------------
    // Video output location
    // ------------------------------------------------------------------

    /** Directory the finished video is written to (Videos/Animated Logo). */
    static Path resolveVideosDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));

        if (os.contains("linux")) {
            String xdg = System.getenv("XDG_VIDEOS_DIR");
            if (xdg != null && !xdg.isBlank()) {
                Path dir = existingOrNull(Path.of(xdg));
                if (dir != null) return dir;
            }
        }
        if (os.contains("mac")) {
            Path movies = home.resolve("Movies");
            if (Files.isDirectory(movies)) return movies;
        } else {
            Path videos = home.resolve("Videos");
            if (Files.isDirectory(videos)) return videos;
            // OneDrive redirects some Windows profiles away from ~/Videos.
            if (os.contains("win")) {
                Path oneDrive = home.resolve("OneDrive").resolve("Videos");
                if (Files.isDirectory(oneDrive)) return oneDrive;
            }
        }
        return home.resolve("Videos");
    }

    private static Path existingOrNull(Path p) {
        return Files.isDirectory(p) ? p : null;
    }

    private static Path freshVideoTarget() throws IOException {
        Path base = resolveVideosDirectory().resolve("Animated Logo");
        Files.createDirectories(base);
        String stamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        for (int i = 0; i < 100; i++) {
            String name = i == 0
                    ? "animated-logo-" + stamp + ".mp4"
                    : "animated-logo-" + stamp + "-" + i + ".mp4";
            Path candidate = base.resolve(name);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("Could not find a free video file name in " + base);
    }

    /** Removes leftover temp frames from old versions (called on init). */
    public static void cleanupTmpDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve(TMP_DIR_NAME);
        if (Files.exists(dir)) {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    AnimatedLogo.LOGGER.warn("Failed to delete {}", path, e);
                }
            });
        } catch (IOException e) {
            AnimatedLogo.LOGGER.warn("Failed to clean up {}", dir, e);
        }
    }

    // ------------------------------------------------------------------
    // User-supplied ffmpeg path (saved in the config)
    // ------------------------------------------------------------------

    /** True when {@code raw} points at an ffmpeg executable or a folder holding one. */
    public static boolean isValidFfmpegPath(String raw) {
        return resolveFfmpegFromPath(raw) != null;
    }

    /**
     * Resolves a path the user saved for their own ffmpeg install. It may be
     * the executable itself, its bin folder, or the install root; returns
     * null when the path is empty or points at nothing useful.
     */
    private static Path resolveFfmpegFromPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path;
        try {
            path = Path.of(raw);
        } catch (InvalidPathException e) {
            return null;
        }
        if (Files.isRegularFile(path) && looksLikeFfmpegFile(path)) {
            return path;
        }
        if (Files.isDirectory(path)) {
            Path exe = path.resolve("ffmpeg.exe");
            if (Files.isRegularFile(exe)) {
                return exe;
            }
            Path bin = path.resolve("bin").resolve("ffmpeg.exe");
            if (Files.isRegularFile(bin)) {
                return bin;
            }
        }
        return null;
    }

    private static boolean looksLikeFfmpegFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("ffmpeg");
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    public enum Phase {
        /** Waiting for the user to press OK on the status screen. */
        CONFIRM_START, NEEDS_FFMPEG, INSTALLING, ENCODING, DONE, CANCELLED, FAILED;

        public boolean isTerminal() {
            return this == DONE || this == CANCELLED || this == FAILED;
        }
    }

    /** All mutable export state, shared between the render thread and the UI. */
    public static final class RecorderSession {
        public final Screen returnScreen;
        public volatile Phase phase;
        /** Progress of the render/encode pass (render thread). */
        public volatile int encodedFrames;
        public volatile int frameCount;
        public volatile boolean cancelRequested;
        public volatile String errorMessage;
        public volatile Path outputFile;
        /** True while ffmpeg is adding the intro sound to the finished video. */
        public volatile boolean muxingAudio;
        /** True when the intro sound made it into the saved video. */
        public volatile boolean soundIncluded;
        /** Why there is no sound in the video (null when sound was added). */
        public volatile String soundMessage;
        /** Keep the sound out of the video (set by the ask screen). */
        public volatile boolean soundWanted = true;
        /** Set by the status screen: download and install ffmpeg, then record. */
        public volatile boolean installRequested;
        /** Set by the status screen: skip the sound and record the video only. */
        public volatile boolean recordWithoutSound;
        /** Set by the status screen's OK button to start the export. */
        public volatile boolean startConfirmed;
        /** Set by the status screen after it saved a user-supplied ffmpeg path. */
        public volatile boolean pathProvided;
        /** 0-100 progress of the automatic ffmpeg download. */
        public volatile int installProgress;

        private RecorderSession(Screen returnScreen) {
            this.returnScreen = returnScreen;
            this.phase = Phase.ENCODING;
            this.frameCount = TOTAL_FRAMES;
        }

        private void startEncodeThread() {
            Thread encoder = new Thread(this::encodeLoop, "Animated Logo Video Renderer");
            encoder.setDaemon(true);
            encoder.start();
        }

        private void encodeLoop() {
            try {
                // The status screen confirms the export first. ffmpeg (which
                // adds the startup sound) is only searched for after OK is
                // pressed, so the confirmation can promise the search.
                this.phase = Phase.CONFIRM_START;
                while (!this.cancelRequested && !this.startConfirmed) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (!this.startConfirmed || this.cancelRequested) {
                    this.phase = Phase.CANCELLED;
                    this.errorMessage = "Recording cancelled.";
                    return;
                }

                // The sound needs ffmpeg, which is not part of the game. When
                // it is missing, the status screen first asks whether to
                // download it (Windows can install it automatically; other
                // systems get a hint to install it manually) or to record the
                // video without sound.
                if (this.soundWanted) {
                    if (findFfmpeg() == null) {
                        if (!canAutoInstallFfmpeg()) {
                            this.soundWanted = false;
                            this.soundMessage = "Saved without sound: ffmpeg was not found. "
                                    + "Install ffmpeg, then record again to include the intro sound.";
                            AnimatedLogo.LOGGER.warn("Intro sound not added: {}", this.soundMessage);
                        } else {
                            this.phase = Phase.NEEDS_FFMPEG;
                            while (!this.cancelRequested && !this.installRequested
                                    && !this.recordWithoutSound && !this.pathProvided) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            if (this.cancelRequested || (!this.installRequested
                                    && !this.recordWithoutSound && !this.pathProvided)) {
                                this.phase = Phase.CANCELLED;
                                this.errorMessage = "Recording cancelled.";
                                return;
                            }
                            if (this.recordWithoutSound) {
                                this.soundWanted = false;
                                this.soundMessage = "Recorded without sound, as chosen.";
                            } else if (this.pathProvided) {
                                // The status screen already validated and saved
                                // the path, so there is nothing to install.
                            } else {
                                this.phase = Phase.INSTALLING;
                                if (!installFfmpeg()) {
                                    this.phase = this.cancelRequested ? Phase.CANCELLED : Phase.FAILED;
                                    this.errorMessage = this.cancelRequested
                                            ? "Recording cancelled."
                                            : "Installing ffmpeg failed: " + this.errorMessage;
                                    return;
                                }
                            }
                        }
                    }
                }

                this.phase = Phase.ENCODING;
                BufferedImage[] sheets = loadSheets();
                this.outputFile = freshVideoTarget();
                Mp4Encoder encoder = Mp4Encoder.open(this.outputFile, TARGET_FPS);
                try {
                    for (int i = 0; i < this.frameCount; i++) {
                        if (this.cancelRequested) {
                            break;
                        }
                        encoder.encode(renderFrame(sheets, i));
                        this.encodedFrames = i + 1;
                    }
                    encoder.finish();
                } catch (Exception e) {
                    encoder.abort();
                    throw e;
                }

                if (this.cancelRequested || this.encodedFrames < this.frameCount) {
                    Files.deleteIfExists(this.outputFile);
                    this.phase = Phase.CANCELLED;
                    this.errorMessage = "Recording cancelled.";
                    return;
                }
                if (this.soundWanted) {
                    addIntroSound();
                }
                if (this.cancelRequested) {
                    Files.deleteIfExists(this.outputFile);
                    this.phase = Phase.CANCELLED;
                    this.errorMessage = "Recording cancelled.";
                    return;
                }
                this.phase = Phase.DONE;
                AnimatedLogo.LOGGER.info("Recording saved to {}", this.outputFile);
            } catch (Exception e) {
                AnimatedLogo.LOGGER.error("Failed to render intro recording", e);
                this.phase = Phase.FAILED;
                this.errorMessage = "Rendering failed: " + e.getMessage();
                if (this.outputFile != null) {
                    try {
                        Files.deleteIfExists(this.outputFile);
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        private static BufferedImage[] loadSheets() throws IOException {
            BufferedImage[] sheets = new BufferedImage[SHEET_COUNT];
            for (int i = 0; i < SHEET_COUNT; i++) {
                String path = "assets/animated-mojang-logo/textures/gui/frame_" + i + ".png";
                try (InputStream in = AnimatedLogoRecorder.class.getResourceAsStream("/" + path)) {
                    if (in == null) {
                        throw new IOException("Missing texture " + path);
                    }
                    BufferedImage sheet = ImageIO.read(in);
                    if (sheet == null || sheet.getWidth() < SHEET_WIDTH || sheet.getHeight() < SHEET_HEIGHT) {
                        throw new IOException("Unexpected frame texture size in " + path);
                    }
                    sheets[i] = sheet;
                }
            }
            return sheets;
        }

        /**
         * Renders output frame {@code index} the way the splash mixin draws
         * it on screen: brand-coloured background, logo sheet sub-frame in
         * the middle, fading in, animating for 3 s and fading out to black.
         */
        private BufferedImage renderFrame(BufferedImage[] sheets, int index) {
            double tMs = index * 1000.0 / TARGET_FPS;
            double logoAlpha;
            double brandAlpha;
            int count;
            if (tMs < FADE_IN_MS) {
                logoAlpha = Math.min(tMs / FADE_IN_MS, 1.0);
                brandAlpha = 1.0;
                count = 0;
            } else if (tMs < FADE_IN_MS + ANIMATION_MS) {
                double progress = (tMs - FADE_IN_MS) / ANIMATION_MS;
                count = Math.min((int) (progress * TOTAL_STEPS), LAST_COUNT);
                logoAlpha = 1.0;
                brandAlpha = 1.0;
            } else {
                double fade = 1.0 - (tMs - FADE_IN_MS - ANIMATION_MS) / FADE_OUT_MS;
                logoAlpha = Math.max(0.0, fade);
                brandAlpha = logoAlpha;
                count = LAST_COUNT;
            }

            int sheetIndex = Math.min(count / STEPS_PER_SHEET, SHEET_COUNT - 1);
            int subFrameY = (count % STEPS_PER_SHEET) / 2 * SUBFRAME_HEIGHT;

            BufferedImage frame = new BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) brandAlpha));
                g.setColor(new Color(BRAND_RGB));
                g.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);

                // Logo geometry mirrors the mixin: half the screen width,
                // 256/1024 aspect, centred.
                int logoWidth = VIDEO_WIDTH / 2;
                int logoHeight = logoWidth * SUBFRAME_HEIGHT / SHEET_WIDTH;
                int x = (VIDEO_WIDTH - logoWidth) / 2;
                int y = (VIDEO_HEIGHT - logoHeight) / 2;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) logoAlpha));
                g.drawImage(sheets[sheetIndex], x, y, x + logoWidth, y + logoHeight,
                        0, subFrameY, SHEET_WIDTH, subFrameY + SUBFRAME_HEIGHT, null);
            } finally {
                g.dispose();
            }
            return frame;
        }

        /**
         * Muxes the bundled startup sound into the finished video with
         * ffmpeg (video stream copied, audio re-encoded as AAC). The audio
         * is delayed by {@link #SOUND_START_DELAY_MS} so it starts when the
         * logo animation does, matching the game. ffmpeg is not bundled with
         * the game, so this is best-effort: when it is missing or fails, the
         * silent video is kept and {@link #soundMessage} explains why.
         */
        private void addIntroSound() {
            if (this.cancelRequested) {
                return;
            }
            Path ffmpeg = findFfmpeg();
            if (ffmpeg == null) {
                this.soundMessage = "Saved without sound: ffmpeg was not found. "
                        + "Install ffmpeg or put ffmpeg.exe next to the game, then record again.";
                AnimatedLogo.LOGGER.warn("Intro sound not added: {}", this.soundMessage);
                return;
            }

            Path soundFile = null;
            Path muxed = null;
            this.muxingAudio = true;
            try {
                soundFile = extractStartupSound();
                // Same directory as the video so the rename stays on one volume.
                muxed = Files.createTempFile(this.outputFile.getParent(), "animated-logo-audio-", ".mp4");
                Process process = new ProcessBuilder(
                        ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y",
                        "-i", this.outputFile.toString(),
                        "-i", soundFile.toString(),
                        "-map", "0:v:0", "-map", "1:a:0",
                        "-c:v", "copy", "-c:a", "aac", "-b:a", "160k",
                        // Delaying the audio puts a leading silence before it;
                        // no -shortest here so the sound's tail (which outlaps
                        // the fade-out, like in the game) is not cut off.
                        "-af", "adelay=" + SOUND_START_DELAY_MS + ":all=1",
                        "-movflags", "+faststart",
                        muxed.toString())
                        .redirectErrorStream(true)
                        .start();
                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                String output = finished
                        ? new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        : "";
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("ffmpeg timed out");
                }
                if (process.exitValue() != 0) {
                    throw new IOException("ffmpeg failed: " + output.trim());
                }
                Files.move(muxed, this.outputFile, StandardCopyOption.REPLACE_EXISTING);
                muxed = null;
                this.soundIncluded = true;
                AnimatedLogo.LOGGER.info("Added the intro sound to {}", this.outputFile);
            } catch (Exception e) {
                this.soundMessage = "Saved without sound: " + e.getMessage();
                AnimatedLogo.LOGGER.warn("Could not add the intro sound to {}", this.outputFile, e);
            } finally {
                this.muxingAudio = false;
                if (muxed != null) {
                    deleteQuietly(muxed);
                }
                if (soundFile != null) {
                    deleteQuietly(soundFile);
                }
            }
        }

        /** The ffmpeg executable to run, or null when none can be found. */
        private static Path findFfmpeg() {
            // Highest priority: the path the user saved in the config.
            Path configured = resolveFfmpegFromPath(ModConfig.get().ffmpegPath());
            if (configured != null) {
                return configured;
            }
            Path gameDir = FabricLoader.getInstance().getGameDir();
            for (String name : new String[] {"animated-logo-ffmpeg/ffmpeg.exe", "ffmpeg.exe", "ffmpeg"}) {
                Path candidate = gameDir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            // winget installs land in %LOCALAPPDATA%\Microsoft\WinGet\Links;
            // check it directly so a game started before the install still
            // finds ffmpeg even when its inherited PATH is stale.
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                Path winGet = Path.of(localAppData, "Microsoft", "WinGet", "Links", "ffmpeg.exe");
                if (Files.isRegularFile(winGet)) {
                    return winGet;
                }
            }
            return runs(new ProcessBuilder("ffmpeg", "-version")) ? Path.of("ffmpeg") : null;
        }

        private static Path extractStartupSound() throws IOException {
            Path dir = FabricLoader.getInstance().getGameDir().resolve(TMP_DIR_NAME);
            Files.createDirectories(dir);
            Path target = dir.resolve("startup.ogg");
            try (InputStream in = AnimatedLogoRecorder.class.getResourceAsStream("/" + STARTUP_SOUND_PATH)) {
                if (in == null) {
                    throw new IOException("missing bundled sound " + STARTUP_SOUND_PATH);
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        }

        private static boolean runs(ProcessBuilder command) {
            try {
                Process process = command.redirectErrorStream(true).start();
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                process.getInputStream().readAllBytes();
                return finished && process.exitValue() == 0;
            } catch (IOException | InterruptedException e) {
                return false;
            }
        }

        private static void deleteQuietly(Path file) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }

        // ------------------------------------------------------------------
        // Automatic ffmpeg install (Windows only)
        // ------------------------------------------------------------------

        private static boolean canAutoInstallFfmpeg() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            return os.contains("win") && arch.contains("64");
        }

        /**
         * Downloads the newest Windows ffmpeg essentials build and unpacks
         * ffmpeg.exe into the game directory (animated-logo-ffmpeg, a
         * persistent folder that {@link #findFfmpeg()} also checks). The
         * download can be aborted via {@link #cancelRequested}; progress is
         * reported through {@link #installProgress} for the status screen.
         * Returns false (with {@link #errorMessage} filled) on failure.
         */
        private boolean installFfmpeg() {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("animated-logo-ffmpeg");
            Path exe = dir.resolve("ffmpeg.exe");
            Path part = dir.resolve("ffmpeg.exe.part");
            Path zip = null;
            try {
                Files.createDirectories(dir);
                zip = Files.createTempFile(dir, "download-", ".zip");
                downloadFfmpegZip(zip);
                if (this.cancelRequested) {
                    return false;
                }
                extractFfmpegExe(zip, part);
                if (this.cancelRequested) {
                    return false;
                }
                Files.move(part, exe, StandardCopyOption.REPLACE_EXISTING);
                this.installProgress = 100;
                // Remember where it went so later recordings skip the download.
                ModConfig.get().setFfmpegPath(exe.toString());
                AnimatedLogo.LOGGER.info("Installed ffmpeg to {}", exe);
                return true;
            } catch (IOException | InterruptedException e) {
                this.errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                return false;
            } finally {
                if (zip != null) {
                    deleteQuietly(zip);
                }
                deleteQuietly(part);
            }
        }

        private void downloadFfmpegZip(Path zip) throws IOException, InterruptedException {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveDownloadUrl(client)))
                    .timeout(Duration.ofMinutes(15))
                    .header("User-Agent", "Animated-Logo-Mod/2.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("download returned HTTP " + response.statusCode());
            }
            long expected = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(zip)) {
                byte[] buffer = new byte[64 * 1024];
                long copied = 0;
                int lastPercent = -1;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    if (this.cancelRequested) {
                        throw new IOException("cancelled");
                    }
                    out.write(buffer, 0, n);
                    copied += n;
                    if (expected > 0) {
                        int percent = (int) (copied * 100 / expected);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            this.installProgress = percent;
                        }
                    }
                }
            }
            if (Files.size(zip) < 1_000_000L) {
                throw new IOException("the downloaded file was unexpectedly small");
            }
        }

        /**
         * The newest Windows essentials build is published as a GitHub
         * release asset of GyanD/codexffmpeg; gyan.dev is the fallback when
         * the GitHub API cannot be reached.
         */
        private static String resolveDownloadUrl(HttpClient client) {
            try {
                HttpRequest request = HttpRequest.newBuilder(
                                URI.create("https://api.github.com/repos/GyanD/codexffmpeg/releases/latest"))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Animated-Logo-Mod/2.0")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Matcher matcher = Pattern.compile("\"browser_download_url\": \"([^\"]*essentials_build[.]zip)\"")
                            .matcher(response.body());
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            } catch (IOException | InterruptedException e) {
                // Fall back to the mirror below.
            }
            return "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
        }

        private void extractFfmpegExe(Path zip, Path part) throws IOException {
            boolean found = false;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory() && entry.getName().replace('\\', '/').endsWith("/bin/ffmpeg.exe")) {
                        found = true;
                        try (OutputStream out = Files.newOutputStream(part)) {
                            byte[] buffer = new byte[64 * 1024];
                            int n;
                            while ((n = zis.read(buffer)) > 0) {
                                if (this.cancelRequested) {
                                    throw new IOException("cancelled");
                                }
                                out.write(buffer, 0, n);
                            }
                        }
                        break;
                    }
                }
            }
            if (!found) {
                throw new IOException("ffmpeg.exe was not inside the downloaded archive");
            }
        }
    }
}
