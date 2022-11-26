package com.example.googlecloudvisionapi;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.ColorInfo;
import com.google.api.services.vision.v1.model.DominantColorsAnnotation;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.LocalizedObjectAnnotation;
import com.google.api.services.vision.v1.model.LocationInfo;
import com.google.api.services.vision.v1.model.SafeSearchAnnotation;
import com.google.api.services.vision.v1.model.TextAnnotation;
import com.google.api.services.vision.v1.model.WebDetection;
import com.google.api.services.vision.v1.model.WebEntity;
import com.google.api.services.vision.v1.model.WebImage;
import com.google.api.services.vision.v1.model.WebLabel;
import com.google.api.services.vision.v1.model.WebPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button start;
    ImageView imageView;
    TextView result, resultText;
    Vision vision;
    ProgressDialog progressDialog;
    ActivityResultLauncher<Intent> activityResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        start = findViewById(R.id.analyze);
        imageView = findViewById(R.id.image);
        result = findViewById(R.id.result);
        resultText = findViewById(R.id.result_text);
        result.setMovementMethod(new ScrollingMovementMethod());
        resultText.setMovementMethod(new ScrollingMovementMethod());
        progressDialog = new ProgressDialog(this);

        //Initial a Google Cloud Vision instance
        //API key is AIzaSyA87d2F52KH0QgxpYI4yxxpsCEzvbvs5i8
        Vision.Builder visionBuilder = new Vision.Builder(new NetHttpTransport(), new GsonFactory(), null);
        visionBuilder.setVisionRequestInitializer(new VisionRequestInitializer("AIzaSyA87d2F52KH0QgxpYI4yxxpsCEzvbvs5i8"));
        vision = visionBuilder.build();

        //Get result from Gallery
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent intent = result.getData();
                    if (intent != null) {
                        Uri image = intent.getData();
                        imageView.setImageURI(image);
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), image);
                            callCloudVisionAPI(bitmap);
                        } catch (Exception e) {
                            Toast.makeText(this, "Cannot convert Uri to bitmap", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this,
                                "No image selected", Toast.LENGTH_SHORT).show();
                    }
                });

        start.setOnClickListener(view -> openGallery());
    }

    /**
     * Send Intent to open the gallery
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        activityResultLauncher.launch(intent);
    }

    /**
     * Transform bitmap to Base-64 encoded image, since Cloud Vision API needs this format
     *
     * @param bitmap bitmap for the image
     * @return a base-64 encoded image
     */
    private Image getBase64Image(Bitmap bitmap) {
        Image image = new Image();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        image.encodeContent(bytes);
        return image;
    }

    /**
     * Get the labels from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getLabelsFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Labels: \n");
        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
        if (labels != null) {
            for (EntityAnnotation label : labels) {
                message.append(label.getDescription()).append(": ");
                message.append(label.getScore()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get the text from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getTextFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Text: \n");
        TextAnnotation texts = response.getResponses().get(0).getFullTextAnnotation();
        if (texts != null) {
            message.append(texts.getText()).append("\n");
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get face detection (emotion) from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getFaceFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Face: \n");
        List<FaceAnnotation> faces = response.getResponses().get(0).getFaceAnnotations();
        if (faces != null) {
            if (faces.size() > 1)
                message.append("There are ").append(faces.size()).append(" faces \n");
            else
                message.append("There are ").append(faces.size()).append(" face \n");
            for (int i = 0; i < faces.size(); i++) {
                message.append("face ").append(i).append("\n");
                message.append("Joy: ").append(faces.get(i).getJoyLikelihood()).append("\n");
                message.append("Anger: ").append(faces.get(i).getAngerLikelihood()).append("\n");
                message.append("Surprise: ").append(faces.get(i).getSurpriseLikelihood()).append("\n");
                message.append("Sorrow: ").append(faces.get(i).getSorrowLikelihood()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get object name and confidence score from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getObjectFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Objects: \n");
        List<LocalizedObjectAnnotation> objects = response.getResponses().get(0).getLocalizedObjectAnnotations();
        if (objects != null) {
            for (LocalizedObjectAnnotation object : objects) {
                message.append(object.getName()).append(": ").append(object.getScore()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get safe search feedback from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getSafeSearchFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Safe Search: \n");
        SafeSearchAnnotation annotation = response.getResponses().get(0).getSafeSearchAnnotation();
        if (annotation != null) {
            message.append("Adult: ").append(annotation.getAdult()).append("\n");
            message.append("Medical: ").append(annotation.getMedical()).append("\n");
            message.append("Spoof: ").append(annotation.getSpoof()).append("\n");
            message.append("Violence: ").append(annotation.getViolence()).append("\n");
            message.append("Racy: ").append(annotation.getRacy()).append("\n");
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get logo description from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getLogoFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Logo: \n");
        List<EntityAnnotation> entityAnnotationList = response.getResponses().get(0).getLogoAnnotations();
        if (entityAnnotationList != null) {
            for (EntityAnnotation entity : entityAnnotationList) {
                message.append(entity.getDescription()).append(": ");
                message.append(entity.getScore()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get landmark description and location from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getLandMarkFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Landmarks: \n");
        List<EntityAnnotation> entityAnnotationList = response.getResponses().get(0).getLandmarkAnnotations();
        if (entityAnnotationList != null) {
            for (EntityAnnotation entity : entityAnnotationList) {
                LocationInfo info = entity.getLocations().listIterator().next();
                message.append("Name: ").append(entity.getDescription()).append("\n");
                message.append("Location: ").append(info.getLatLng()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get the color info from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getImagePropertyFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Image Property: \n");
        DominantColorsAnnotation colors = response.getResponses().get(0).getImagePropertiesAnnotation().getDominantColors();
        if (colors != null) {
            for (ColorInfo color : colors.getColors()) {
                message.append("Fraction: ").append(color.getPixelFraction()).append("\n");
                message.append("R: ").append(color.getColor().getRed()).append("\n");
                message.append("G: ").append(color.getColor().getGreen()).append("\n");
                message.append("B: ").append(color.getColor().getBlue()).append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Get web detection (label, Pages with matching images, Pages with partially matching images,
     * Pages with fully matching images, Pages with visually similar images) from BatchAnnotateImagesResponse
     *
     * @param response BatchAnnotateImagesResponse
     * @return String
     */
    private String getWebDetectionFromResponse(BatchAnnotateImagesResponse response) {
        StringBuilder message = new StringBuilder();
        message.append("Web Detection: \n");
        WebDetection annotation = response.getResponses().get(0).getWebDetection();
        if (annotation != null) {
            for (WebEntity entity : annotation.getWebEntities()) {
                message.append(entity.getDescription()).append(": ").append(entity.getScore()).append("\n");
            }
            for (WebLabel label : annotation.getBestGuessLabels()) {
                message.append("Best guess label: ").append(label.getLabel()).append("\n");
            }
            message.append("\n");
            message.append("Pages with matching images: \n");
            for (WebPage page : annotation.getPagesWithMatchingImages()) {
                message.append(page.getUrl()).append("\n");
                message.append("\n");
            }
            message.append("Pages with partially matching images:\n");
            for (WebImage image : annotation.getPartialMatchingImages()) {
                message.append(image.getUrl()).append("\n");
                message.append("\n");
            }
            message.append("Pages with fully matching images:\n");
            for (WebImage image : annotation.getFullMatchingImages()) {
                message.append(image.getUrl()).append("\n");
                message.append("\n");
            }
            message.append("Pages with visually similar images:\n");
            for (WebImage image : annotation.getVisuallySimilarImages()) {
                message.append(image.getUrl()).append("\n");
                message.append("\n");
            }
        } else {
            message.append("No result\n");
        }
        return message.toString();
    }

    /**
     * Send the request to Cloud Vision
     *
     * @param bitmap bitmap for chosen image
     */
    @SuppressLint("StaticFieldLeak")
    private void callCloudVisionAPI(final Bitmap bitmap) {
        progressDialog = ProgressDialog.show(this, null, "Scanning image with Google Cloud Vision...", true);

        //Create a new thread to perform Vision API request (Network operation)
        //(This AsyncTask is deprecated on API level 30) Recommend: Use the standard java.util.concurrent
        new AsyncTask<Object, Void, BatchAnnotateImagesResponse>() {
            @Override
            protected BatchAnnotateImagesResponse doInBackground(Object... objects) {
                //All the features from Cloud Vision API that we gonna use
                List<Feature> features = new ArrayList<>();
                //Add label detection feature
                Feature labelDetection = new Feature();
                labelDetection.setType("LABEL_DETECTION");
                labelDetection.setMaxResults(10);
                features.add(labelDetection);
                //Add text detection feature
                Feature textDetection = new Feature();
                textDetection.setType("TEXT_DETECTION");
                textDetection.setMaxResults(10);
                features.add(textDetection);
                //Add face detection feature
                Feature faceDetection = new Feature();
                faceDetection.setType("FACE_DETECTION");
                faceDetection.setMaxResults(10);
                features.add(faceDetection);
                //Add Object detection feature
                Feature objectDetection = new Feature();
                objectDetection.setType("OBJECT_LOCALIZATION");
                faceDetection.setMaxResults(10);
                features.add(objectDetection);
                //Add safe search feature
                Feature safeSearchDetection = new Feature();
                safeSearchDetection.setType("SAFE_SEARCH_DETECTION");
                safeSearchDetection.setMaxResults(10);
                features.add(safeSearchDetection);
                //Add Logo detection feature
                Feature logoDetection = new Feature();
                logoDetection.setType("LOGO_DETECTION");
                logoDetection.setMaxResults(10);
                features.add(logoDetection);
                //Add Landmark detection feature
                Feature landMarkDetection = new Feature();
                landMarkDetection.setType("LANDMARK_DETECTION");
                landMarkDetection.setMaxResults(10);
                features.add(landMarkDetection);
                //Add image property detection feature
                Feature imagePropertyDetection = new Feature();
                imagePropertyDetection.setType("IMAGE_PROPERTIES");
                imagePropertyDetection.setMaxResults(10);
                features.add(imagePropertyDetection);
                //Add web search detection feature
                Feature webDetection = new Feature();
                webDetection.setType("WEB_DETECTION");
                webDetection.setMaxResults(10);
                features.add(webDetection);
                //Prepare image and API call
                List<AnnotateImageRequest> imageRequestList = new ArrayList<>();
                AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
                Image base64Image = getBase64Image(bitmap);
                annotateImageRequest.setImage(base64Image);
                annotateImageRequest.setFeatures(features);
                imageRequestList.add(annotateImageRequest);
                BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
                batchAnnotateImagesRequest.setRequests(imageRequestList);
                //Call the Cloud Vision API
                try {
                    Vision.Images.Annotate request = vision.images().annotate(batchAnnotateImagesRequest);
                    request.setDisableGZipContent(true);
                    Log.d("CloudVisionAPI", "Sending request to Google Cloud");
                    return request.execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @SuppressLint("SetTextI18n")
            @Override
            protected void onPostExecute(BatchAnnotateImagesResponse batchAnnotateImagesResponse) {
                progressDialog.dismiss();
                if (batchAnnotateImagesResponse != null) {
                    String label = getLabelsFromResponse(batchAnnotateImagesResponse);
                    String text = getTextFromResponse(batchAnnotateImagesResponse);
                    String face = getFaceFromResponse(batchAnnotateImagesResponse);
                    String object = getObjectFromResponse(batchAnnotateImagesResponse);
                    String safeSearch = getSafeSearchFromResponse(batchAnnotateImagesResponse);
                    String landmark = getLandMarkFromResponse(batchAnnotateImagesResponse);
                    String logo = getLogoFromResponse(batchAnnotateImagesResponse);
                    String colors = getImagePropertyFromResponse(batchAnnotateImagesResponse);
                    String web = getWebDetectionFromResponse(batchAnnotateImagesResponse);
                    result.setText(label + "\n" + text + "\n" + face + "\n" + safeSearch + "\n" + landmark + "\n" + colors);
                    resultText.setText(object + "\n" + logo + "\n" + web);
                } else {
                    Toast.makeText(MainActivity.this, "Cannot get repose from Google", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }
}