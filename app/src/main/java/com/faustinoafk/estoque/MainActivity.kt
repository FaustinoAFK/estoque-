package com.faustinoafk.estoque

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.faustinoafk.estoque.data.local.StockDatabase
import com.faustinoafk.estoque.data.repository.StockRepository
import com.faustinoafk.estoque.ui.components.AdaptiveBentoLayout
import com.faustinoafk.estoque.ui.theme.MyApplicationTheme
import com.faustinoafk.estoque.ui.viewmodel.StockViewModel

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
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                AdaptiveBentoLayout(viewModel = viewModel)
            }
        }
    }
}
