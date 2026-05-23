package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.StockDatabase
import com.example.data.repository.StockRepository
import com.example.ui.components.AdaptiveBentoLayout
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StockViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Persistent storage dependencies
        val database = StockDatabase.getDatabase(applicationContext)
        val repository = StockRepository(database.stockDao(), applicationContext)
        val prefs = applicationContext.getSharedPreferences("stocksync_shared_prefs", Context.MODE_PRIVATE)

        // Custom ViewModel Factory
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockViewModel(repository, prefs) as T
            }
        }

        val viewModel = ViewModelProvider(this, factory)[StockViewModel::class.java]

        setContent {
            MyApplicationTheme {
                AdaptiveBentoLayout(viewModel = viewModel)
            }
        }
    }
}
