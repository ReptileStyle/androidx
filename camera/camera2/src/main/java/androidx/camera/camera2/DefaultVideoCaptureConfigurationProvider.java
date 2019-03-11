/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.camera2;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.util.Log;
import android.util.Rational;
import android.view.WindowManager;

import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ConfigurationProvider;
import androidx.camera.core.SessionConfiguration;
import androidx.camera.core.VideoCaptureUseCase;
import androidx.camera.core.VideoCaptureUseCaseConfiguration;

import java.util.Arrays;
import java.util.List;

/** Provides defaults for {@link VideoCaptureUseCaseConfiguration} in the Camera2 implementation. */
final class DefaultVideoCaptureConfigurationProvider
        implements ConfigurationProvider<VideoCaptureUseCaseConfiguration> {
    private static final String TAG = "DefVideoCapProvider";
    private static final Rational DEFAULT_ASPECT_RATIO_16_9 = new Rational(16, 9);
    private static final Rational DEFAULT_ASPECT_RATIO_9_16 = new Rational(9, 16);

    private final CameraFactory mCameraFactory;
    private final WindowManager mWindowManager;

    DefaultVideoCaptureConfigurationProvider(CameraFactory cameraFactory, Context context) {
        mCameraFactory = cameraFactory;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public VideoCaptureUseCaseConfiguration getConfiguration(LensFacing lensFacing) {
        VideoCaptureUseCaseConfiguration.Builder builder =
                VideoCaptureUseCaseConfiguration.Builder.fromConfig(
                        VideoCaptureUseCase.DEFAULT_CONFIG.getConfiguration(lensFacing));

        // SessionConfiguration containing all intrinsic properties needed for VideoCaptureUseCase
        SessionConfiguration.Builder sessionBuilder = new SessionConfiguration.Builder();
        // TODO(b/114762170): Must set to preview here until we allow for multiple template types
        sessionBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);

        // Add options to UseCaseConfiguration
        builder.setDefaultSessionConfiguration(sessionBuilder.build());
        builder.setOptionUnpacker(Camera2OptionUnpacker.INSTANCE);

        List<LensFacing> lensFacingList;

        // Add default lensFacing if we can
        if (lensFacing == LensFacing.FRONT) {
            lensFacingList = Arrays.asList(LensFacing.FRONT, LensFacing.BACK);
        } else {
            lensFacingList = Arrays.asList(LensFacing.BACK, LensFacing.FRONT);
        }

        try {
            String defaultId = null;

            for (LensFacing lensFacingCandidate : lensFacingList) {
                defaultId = mCameraFactory.cameraIdForLensFacing(lensFacingCandidate);
                if (defaultId != null) {
                    builder.setLensFacing(lensFacingCandidate);
                    break;
                }
            }

            int targetRotation = mWindowManager.getDefaultDisplay().getRotation();
            int rotationDegrees = CameraX.getCameraInfo(defaultId).getSensorRotationDegrees(
                    targetRotation);
            boolean isRotateNeeded = (rotationDegrees == 90 || rotationDegrees == 270);
            builder.setTargetRotation(targetRotation);
            builder.setTargetAspectRatio(
                    isRotateNeeded ? DEFAULT_ASPECT_RATIO_9_16 : DEFAULT_ASPECT_RATIO_16_9);
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine default lens facing for VideoCaptureUseCase.", e);
        }

        return builder.build();
    }
}
