package ai.newrun.cameraopencv;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {

    static {
        System.loadLibrary("tensorflow_inference");
    }

    private String INPUT_NAME = "input_1";
    private String OUTPUT_NAME = "output_1";

    private static final String MODEL_PATH = "file:///android_asset/squeezenet.pb";
    private static final String LABEL_PATH = "file:///android_asset/labels.json";

    @BindView(R.id.HelloOpenCvView)
    CameraBridgeViewBase mOpenCvCameraView;
    @BindView(R.id.resultsView)
    TextView resultsView;

    private TensorFlowInferenceInterface tf;

    //ARRAY TO HOLD THE PREDICTIONS AND FLOAT VALUES TO HOLD THE IMAGE DATA
    float[] PREDICTIONS = new float[1000];
    private float[] floatValues;
    private int[] INPUT_SIZE = {224, 224, 3};

    private boolean isProcessing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        tf = new TensorFlowInferenceInterface(getAssets(), MODEL_PATH);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        Mat rgba = inputFrame.rgba();

        // Convert Mat to bitmap for prediction
        final Bitmap bitmap =
                Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(rgba, bitmap);

        // predict
        if (!isProcessing) {
            predict(bitmap);
        }

        return rgba;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this,
                mLoaderCallback);
    }

    public void predict(final Bitmap bitmap) {

        isProcessing = true;

        //Runs inference in background thread
        new AsyncTask<Integer, Integer, Integer>() {

            @Override

            protected Integer doInBackground(Integer... params) {

                //Resize the image into 224 x 224
                Bitmap resized_image = ImageUtils.processBitmap(bitmap, 224);

                //Normalize the pixels
                floatValues = ImageUtils.normalizeBitmap(resized_image, 224, 127.5f, 1.0f);

                //Pass input into the tensorflow
                tf.feed(INPUT_NAME, floatValues, 1, 224, 224, 3);

                //compute predictions
                tf.run(new String[]{OUTPUT_NAME});

                //copy the output into the PREDICTIONS array
                tf.fetch(OUTPUT_NAME, PREDICTIONS);

                //Obtained highest prediction
                Object[] results = ImageUtils.argmax(PREDICTIONS);

                int class_index = (Integer) results[0];
                float confidence = (Float) results[1];

                try {
                    final String conf = String.valueOf(confidence * 100).substring(0, 5);

                    //Convert predicted class index into actual label name
                    final String label = ImageUtils.getLabel(getAssets().open("labels.json"), class_index);

                    //Display result on UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultsView.setText(label + " : " + conf + "%");
                        }
                    });

                    isProcessing = false;

                } catch (Exception e) {

                }
                return 0;
            }
        }.execute(0);
    }
}
