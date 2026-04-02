package com.example.basicgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.basicgallery.data.MediaStoreGalleryRepository
import com.example.basicgallery.ui.gallery.GalleryRoute
import com.example.basicgallery.ui.gallery.GalleryViewModel
import com.example.basicgallery.ui.gallery.GalleryViewModelFactory
import com.example.basicgallery.ui.theme.BasicGalleryTheme

class MainActivity : ComponentActivity() {
    private val galleryViewModel: GalleryViewModel by viewModels {
        GalleryViewModelFactory(
            repository = MediaStoreGalleryRepository(contentResolver)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BasicGalleryTheme {
                GalleryRoute(viewModel = galleryViewModel)
            }
        }
    }
}
