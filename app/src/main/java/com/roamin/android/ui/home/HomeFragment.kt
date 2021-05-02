package com.roamin.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.roamin.android.R
import com.roamin.android.helper.DemoUtils
import uk.co.appoly.arcorelocation.LocationMarker
import uk.co.appoly.arcorelocation.LocationScene
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException


class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private var installRequested = false
    private var hasFinishedLoading = false

    private var loadingMessageSnackbar: Snackbar? = null

    // Renderables for this example
    private var andyRenderable: ModelRenderable? = null
    private var exampleLayoutRenderable: ViewRenderable? = null

    // Our ARCore-Location scene
    private var arSceneView: ArSceneView? = null
    private var locationScene: LocationScene? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        homeViewModel.text.observe(viewLifecycleOwner, Observer {

        })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arSceneView = view.findViewById<ArSceneView>(R.id.ar_scene_view)

        val exampleLayout: CompletableFuture<ViewRenderable> = ViewRenderable.builder()
            .setView(activity, R.layout.example_layout)
            .build()

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        val andy: CompletableFuture<ModelRenderable> = ModelRenderable.builder()
            .setSource(activity, R.raw.andy)
            .build()

        CompletableFuture.allOf(
            exampleLayout,
            andy
        )
            .handle<Any?> { notUsed: Void?, throwable: Throwable? ->
                // When you build a Renderable, Sceneform loads its resources in the background while
                // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                // before calling get().
                if (throwable != null) {
                    DemoUtils.displayError(
                        activity?.applicationContext!!,
                        "Unable to load renderables",
                        throwable
                    )
                    return@handle null
                }
                try {
                    exampleLayoutRenderable = exampleLayout.get()
                    andyRenderable = andy.get()
                    hasFinishedLoading = true
                } catch (ex: InterruptedException) {
                    DemoUtils.displayError(
                        activity?.applicationContext!!,
                        "Unable to load renderables",
                        ex
                    )
                } catch (ex: ExecutionException) {
                    DemoUtils.displayError(
                        activity?.applicationContext!!,
                        "Unable to load renderables",
                        ex
                    )
                }
                null
            }

        arSceneView!!
            .scene
            .addOnUpdateListener { frameTime ->
                if (!hasFinishedLoading) {
                    return@addOnUpdateListener
                }
                if (locationScene == null) {
                    // If our locationScene object hasn't been setup yet, this is a good time to do it
                    // We know that here, the AR components have been initiated.
                    locationScene = LocationScene(activity, arSceneView)

                    // Now lets create our location markers.
                    // First, a layout
                    val layoutLocationMarker = LocationMarker(
                        -4.849509,
                        42.814603,
                        getExampleView()
                    )

                    // An example "onRender" event, called every frame
                    // Updates the layout with the markers distance
                    layoutLocationMarker.renderEvent =
                        LocationNodeRender { node ->
                            val eView = exampleLayoutRenderable!!.view
                            val distanceTextView = eView.findViewById<TextView>(R.id.textView2)
                            distanceTextView.text = node.distance.toString() + "M"
                        }
                    // Adding the marker
                    locationScene!!.mLocationMarkers.add(layoutLocationMarker)

                    // Adding a simple location marker of a 3D model
                    locationScene!!.mLocationMarkers.add(
                        LocationMarker(
                            -0.119677,
                            51.478494,
                            getAndy()
                        )
                    )
                }
                val frame = arSceneView?.arFrame ?: return@addOnUpdateListener
                if (frame.camera.trackingState !== TrackingState.TRACKING) {
                    return@addOnUpdateListener
                }
                if (locationScene != null) {
                    locationScene!!.processFrame(frame)
                }
                if (loadingMessageSnackbar != null) {
                    for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                        if (plane.getTrackingState() === TrackingState.TRACKING) {
                            hideLoadingMessage()
                        }
                    }
                }
            }


        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.


        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.
        ARLocationPermissionHelper.requestPermission(activity)
    }

    override fun onResume() {
        super.onResume()

        if (locationScene != null) {
            locationScene?.resume();
        }

        if (arSceneView?.session == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session: Session? = DemoUtils.createArSession(activity, installRequested)
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(activity)
                    return
                } else {
                    arSceneView?.setupSession(session)
                }
            } catch (e: UnavailableException) {
                DemoUtils.handleSessionException(activity, e)
            }
        }

        try {
            arSceneView?.resume()
        } catch (ex: CameraNotAvailableException) {
            DemoUtils.displayError(activity?.applicationContext!!, "Unable to get camera", ex)
            activity?.finish()
            return
        }

        if (arSceneView?.session != null) {
            showLoadingMessage()
        }
    }

    override fun onPause() {
        super.onPause();

        if (locationScene != null) {
            locationScene?.pause();
        }

        arSceneView?.pause();
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView!!.destroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!ARLocationPermissionHelper.hasPermission(activity)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(activity)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(activity);
            } else {
                Toast.makeText(
                    activity, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            }
            activity?.finish();
        }
    }


    /**
     * Example node of a layout
     *
     * @return
     */
    private fun getExampleView(): Node? {
        val base = Node()
        base.setRenderable(exampleLayoutRenderable)
        val c: Context = activity?.applicationContext!!
        // Add  listeners etc here
        val eView = exampleLayoutRenderable!!.view
        eView.setOnTouchListener { v: View?, event: MotionEvent? ->
            Toast.makeText(
                c, "Location marker touched.", Toast.LENGTH_LONG
            )
                .show()
            false
        }
        return base
    }

    /***
     * Example Node of a 3D model
     *
     * @return
     */
    private fun getAndy(): Node? {
        val base = Node()
        base.setRenderable(andyRenderable)
        val c: Context = activity?.applicationContext!!
        base.setOnTapListener { v, event ->
            Toast.makeText(
                c, "Andy touched.", Toast.LENGTH_LONG
            )
                .show()
        }
        return base
    }

    private fun showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar!!.isShownOrQueued()) {
            return
        }
        loadingMessageSnackbar = Snackbar.make(
            (view?.findViewById(android.R.id.content))!!,
            "finding plane...",
            Snackbar.LENGTH_INDEFINITE
        )
        loadingMessageSnackbar!!.view.setBackgroundColor(-0x40cdcdce)
        loadingMessageSnackbar!!.show()
    }

    private fun hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return
        }
        loadingMessageSnackbar?.dismiss()
        loadingMessageSnackbar = null
    }
}