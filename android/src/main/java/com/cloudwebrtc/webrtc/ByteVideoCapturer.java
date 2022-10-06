package com.cloudwebrtc.webrtc;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;

import com.epson.moverio.hardware.audio.AudioManager;
import com.epson.moverio.hardware.camera.CameraDevice;
import com.epson.moverio.hardware.camera.CameraManager;
import com.epson.moverio.hardware.camera.CameraProperty;
import com.epson.moverio.hardware.camera.CaptureDataCallback;
import com.epson.moverio.hardware.camera.CaptureDataCallback2;
import com.epson.moverio.hardware.camera.CaptureStateCallback;
import com.epson.moverio.hardware.camera.CaptureStateCallback2;
import com.epson.moverio.hardware.display.DisplayManager;
import com.epson.moverio.hardware.sensor.SensorDataListener;
import com.epson.moverio.hardware.sensor.SensorManager;
import com.epson.moverio.util.PermissionHelper;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

public class ByteVideoCapturer implements VideoCapturer {
    private interface VideoReader {

        VideoFrame getNextFrame();

        void close();
    }

    /**
     * Read video data from file for the .y4m container.
     */
    @SuppressWarnings("StringSplitter")

    private static class VideoByteReader extends Activity implements VideoReader{
        private CameraManager cameraManager;
        private CameraDevice cameraDevice;
        private ByteBuffer byteStream;
        private final int frameWidth;
        private final int frameHeight;
        private int FRAME_DELIMETER_LENGTH;
        private int offset = 0;

        public VideoByteReader(int frameWidth, int frameHeight) {
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            cameraManager = new CameraManager(this);
            try{
                cameraDevice = cameraManager.open(captureStateCallback, mCaptureDataCallback, null);
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        private CaptureStateCallback2 captureStateCallback = new CaptureStateCallback2() {
            @Override
            public void onCameraOpened() {

            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onCaptureStarted() {

            }

            @Override
            public void onCaptureStopped() {

            }

            @Override
            public void onPreviewStarted() {

            }

            @Override
            public void onPreviewStopped() {

            }

            @Override
            public void onRecordStarted() {

            }

            @Override
            public void onRecordStopped() {

            }

            @Override
            public void onPictureCompleted() {

            }
        };

        private CaptureDataCallback mCaptureDataCallback = new CaptureDataCallback() {
            @Override
            public void onCaptureData(long l, byte[] bytes) {
                byteStream = ByteBuffer.wrap(bytes);
                FRAME_DELIMETER_LENGTH = bytes.length;
            }
        };

        @Override
        public VideoFrame getNextFrame() {
            final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            final JavaI420Buffer buffer = JavaI420Buffer.allocate(frameWidth, frameHeight);

            final ByteBuffer dataY = buffer.getDataY();
            final ByteBuffer dataU = buffer.getDataU();
            final ByteBuffer dataV = buffer.getDataV();
            final int chromaHeight = (frameHeight + 1) / 2;
            final int sizeY = frameHeight * buffer.getStrideY();
            final int sizeU = chromaHeight * buffer.getStrideU();
            final int sizeV = chromaHeight * buffer.getStrideV();


            byte[] frame = new byte[FRAME_DELIMETER_LENGTH];
            byteStream.get(frame, offset, FRAME_DELIMETER_LENGTH);
            offset = offset + FRAME_DELIMETER_LENGTH;

            dataY.put(frame);
            dataU.put(frame);
            dataV.put(frame);

            return new VideoFrame(buffer, 0 /* rotation */, captureTimeNs);
        }

        @Override
        public void close() {
        }
    }

    private static class VideoReaderY4M implements VideoReader {
        private static final String TAG = "VideoReaderY4M";
        private static final String Y4M_FRAME_DELIMETER = "FRAME";
        private static final int FRAME_DELIMETER_LENGTH = Y4M_FRAME_DELIMETER.length() + 1;

        private final int frameWidth;
        private final int frameHeight;
        // First char after header
        private final long videoStart;
        private final RandomAccessFile mediaFile;
        private final FileChannel mediaFileChannel;

        public VideoReaderY4M(String file) throws IOException {
            mediaFile = new RandomAccessFile(file, "r");
            mediaFileChannel = mediaFile.getChannel();
            StringBuilder builder = new StringBuilder();
            for (;;) {
                int c = mediaFile.read();
                if (c == -1) {
                    // End of file reached.
                    throw new RuntimeException("Found end of file before end of header for file: " + file);
                }
                if (c == '\n') {
                    // End of header found.
                    break;
                }
                builder.append((char) c);
            }
            videoStart = mediaFileChannel.position();
            String header = builder.toString();
            String[] headerTokens = header.split("[ ]");
            int w = 0;
            int h = 0;
            String colorSpace = "";
            for (String tok : headerTokens) {
                char c = tok.charAt(0);
                switch (c) {
                    case 'W':
                        w = Integer.parseInt(tok.substring(1));
                        break;
                    case 'H':
                        h = Integer.parseInt(tok.substring(1));
                        break;
                    case 'C':
                        colorSpace = tok.substring(1);
                        break;
                }
            }
            Logging.d(TAG, "Color space: " + colorSpace);
            if (!colorSpace.equals("420") && !colorSpace.equals("420mpeg2")) {
                throw new IllegalArgumentException(
                        "Does not support any other color space than I420 or I420mpeg2");
            }
            if ((w % 2) == 1 || (h % 2) == 1) {
                throw new IllegalArgumentException("Does not support odd width or height");
            }
            frameWidth = w;
            frameHeight = h;
            Logging.d(TAG, "frame dim: (" + w + ", " + h + ")");
        }

        @Override
        public VideoFrame getNextFrame() {
            final long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
            final JavaI420Buffer buffer = JavaI420Buffer.allocate(frameWidth, frameHeight);
            final ByteBuffer dataY = buffer.getDataY();
            final ByteBuffer dataU = buffer.getDataU();
            final ByteBuffer dataV = buffer.getDataV();
            final int chromaHeight = (frameHeight + 1) / 2;
            final int sizeY = frameHeight * buffer.getStrideY();
            final int sizeU = chromaHeight * buffer.getStrideU();
            final int sizeV = chromaHeight * buffer.getStrideV();

            try {
                ByteBuffer frameDelim = ByteBuffer.allocate(FRAME_DELIMETER_LENGTH);
                if (mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
                    // We reach end of file, loop
                    mediaFileChannel.position(videoStart);
                    if (mediaFileChannel.read(frameDelim) < FRAME_DELIMETER_LENGTH) {
                        throw new RuntimeException("Error looping video");
                    }
                }
                String frameDelimStr = new String(frameDelim.array(), Charset.forName("US-ASCII"));
                if (!frameDelimStr.equals(Y4M_FRAME_DELIMETER + "\n")) {
                    throw new RuntimeException(
                            "Frames should be delimited by FRAME plus newline, found delimter was: '"
                                    + frameDelimStr + "'");
                }

                mediaFileChannel.read(dataY);
                mediaFileChannel.read(dataU);
                mediaFileChannel.read(dataV);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return new VideoFrame(buffer, 0 /* rotation */, captureTimeNs);
        }

        @Override
        public void close() {
            try {
                // Closing a file also closes the channel.
                mediaFile.close();
            } catch (IOException e) {
                Logging.e(TAG, "Problem closing file", e);
            }
        }
    }


    private final static String TAG = "FileVideoCapturer";
    private final VideoReader videoReader;
    private CapturerObserver capturerObserver;
    private final Timer timer = new Timer();

    private final TimerTask tickTask = new TimerTask() {
        @Override
        public void run() {
            tick();
        }
    };

    public ByteVideoCapturer(ByteBuffer byteStream,
                             int frame_delimiter_length,
                             int frameWidth,
                             int frameHeight) throws IOException {

        videoReader = new VideoByteReader(frameWidth, frameHeight);


    }

    public void tick() {
        VideoFrame videoFrame = videoReader.getNextFrame();
        capturerObserver.onFrameCaptured(videoFrame);
        videoFrame.release();
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
                           CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        timer.schedule(tickTask, 0, 1000 / framerate);
    }

    @Override
    public void stopCapture() throws InterruptedException {
        timer.cancel();
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        // Empty on purpose
    }

    @Override
    public void dispose() {
        videoReader.close();
    }

    @Override
    public boolean isScreencast() {
        return false;
    }
}