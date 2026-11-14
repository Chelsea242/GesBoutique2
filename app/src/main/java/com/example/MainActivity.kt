package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CaisseStockApp
import com.example.ui.MainViewModel
import com.example.ui.theme.CaisseStockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaisseStockTheme {
                val mainViewModel: MainViewModel = viewModel()
                CaisseStockApp(viewModel = mainViewModel)
            }
        }
    }
}
