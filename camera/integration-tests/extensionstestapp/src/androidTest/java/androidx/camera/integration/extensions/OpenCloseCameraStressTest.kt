/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.integration.extensions

import android.content.Context
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.integration.extensions.util.ExtensionsTestUtil
import androidx.camera.integration.extensions.util.ExtensionsTestUtil.STRESS_TEST_OPERATION_REPEAT_COUNT
import androidx.camera.integration.extensions.util.ExtensionsTestUtil.STRESS_TEST_REPEAT_COUNT
import androidx.camera.integration.extensions.utils.CameraSelectorUtil
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CameraUtil.PreTestCameraIdList
import androidx.camera.testing.SurfaceTextureProvider
import androidx.camera.testing.fakes.FakeLifecycleOwner
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.testutils.RepeatRule
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = 21)
class OpenCloseCameraStressTest(
    private val cameraId: String,
    private val extensionMode: Int
) {
    @get:Rule
    val useCamera = CameraUtil.grantCameraPermissionAndPreTest(
        PreTestCameraIdList(Camera2Config.defaultConfig())
    )

    @get:Rule
    val repeatRule = RepeatRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager
    private lateinit var camera: Camera
    private lateinit var baseCameraSelector: CameraSelector
    private lateinit var extensionCameraSelector: CameraSelector
    private lateinit var preview: Preview
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis
    private var isImageAnalysisSupported = false
    private lateinit var lifecycleOwner: FakeLifecycleOwner

    @Before
    fun setUp(): Unit = runBlocking {
        if (extensionMode != ExtensionMode.NONE) {
            assumeTrue(ExtensionsTestUtil.isTargetDeviceAvailableForExtensions())
        }
        cameraProvider = ProcessCameraProvider.getInstance(context)[10000, TimeUnit.MILLISECONDS]
        extensionsManager = ExtensionsManager.getInstanceAsync(
            context,
            cameraProvider
        )[10000, TimeUnit.MILLISECONDS]

        baseCameraSelector = CameraSelectorUtil.createCameraSelectorById(cameraId)
        assumeTrue(extensionsManager.isExtensionAvailable(baseCameraSelector, extensionMode))

        extensionCameraSelector = extensionsManager.getExtensionEnabledCameraSelector(
            baseCameraSelector,
            extensionMode
        )

        camera = withContext(Dispatchers.Main) {
            lifecycleOwner = FakeLifecycleOwner()
            lifecycleOwner.startAndResume()
            cameraProvider.bindToLifecycle(lifecycleOwner, extensionCameraSelector)
        }

        preview = Preview.Builder().build()
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
        }
        imageCapture = ImageCapture.Builder().build()
        imageAnalysis = ImageAnalysis.Builder().build()

        isImageAnalysisSupported =
            camera.isUseCasesCombinationSupported(preview, imageCapture, imageAnalysis)
    }

    @After
    fun cleanUp(): Unit = runBlocking {
        if (::cameraProvider.isInitialized) {
            withContext(Dispatchers.Main) {
                cameraProvider.unbindAll()
                cameraProvider.shutdown()[10000, TimeUnit.MILLISECONDS]
            }
        }

        if (::extensionsManager.isInitialized) {
            extensionsManager.shutdown()[10000, TimeUnit.MILLISECONDS]
        }
    }

    companion object {
        @JvmStatic
        @get:Parameterized.Parameters(name = "cameraId = {0}, extensionMode = {1}")
        val parameters: Collection<Array<Any>>
            get() = ExtensionsTestUtil.getAllCameraIdModeCombinations()
    }

    @Test
    @RepeatRule.Repeat(times = STRESS_TEST_REPEAT_COUNT)
    fun openCloseCameraStressTest(): Unit = runBlocking {
        for (i in 1..STRESS_TEST_OPERATION_REPEAT_COUNT) {
            val openCameraLatch = CountDownLatch(1)
            val closeCameraLatch = CountDownLatch(1)
            val observer = Observer<CameraState> { state ->
                if (state.type == CameraState.Type.OPEN) {
                    openCameraLatch.countDown()
                } else if (state.type == CameraState.Type.CLOSED) {
                    closeCameraLatch.countDown()
                }
            }

            withContext(Dispatchers.Main) {
                camera.cameraInfo.cameraState.observe(lifecycleOwner, observer)

                if (isImageAnalysisSupported) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        extensionCameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        extensionCameraSelector,
                        preview,
                        imageCapture
                    )
                }
            }

            assertThat(openCameraLatch.await(3000, TimeUnit.MILLISECONDS)).isTrue()

            withContext(Dispatchers.Main) {
                cameraProvider.unbindAll()
            }

            assertThat(closeCameraLatch.await(3000, TimeUnit.MILLISECONDS)).isTrue()

            withContext(Dispatchers.Main) {
                camera.cameraInfo.cameraState.removeObserver(observer)
            }
        }
    }
}
