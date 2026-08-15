package com.example.zodiactableapp

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.zodiactableapp.databinding.ActivityMainBinding
import com.example.zodiactableapp.shared.BetSharedViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedVM: BetSharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 关键：我们自己处理系统栏(insets)，避免不同手机顶部/底部遮挡
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        binding.navView.setupWithNavController(navController)

        buildProfileLauncher(sharedVM.getProfileIds())
        sharedVM.profileIds.observe(this) { buildProfileLauncher(it) }
        showProfileLauncher()
        setupBackHandling()
        applyInsets()
    }

    private fun buildProfileLauncher(profileIds: List<Int>) {
        binding.profileGrid.removeAllViews()

        val canDelete = profileIds.size > 1
        for (profileId in profileIds) {
            val item = makeProfileItem(profileId, canDelete)
            binding.profileGrid.addView(item)
        }

        binding.profileGrid.addView(makeCreateProfileItem())
    }

    private fun makeProfileItem(profileId: Int, canDelete: Boolean): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            layoutParams = makeGridItemLayoutParams()
            background = makeCardBackground(
                fillColor = Color.WHITE,
                strokeColor = Color.rgb(220, 220, 220)
            )
            isClickable = true
            isFocusable = true

            addView(ImageView(context).apply {
                setImageResource(R.mipmap.ic_launcher)
                contentDescription = "${profileId}生肖投注助手"
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            })

            addView(TextView(context).apply {
                text = "${profileId}生肖投注助手"
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            })

            setOnClickListener { enterProfile(profileId) }
            if (canDelete) {
                setOnLongClickListener {
                    confirmDeleteProfile(profileId)
                    true
                }
            }
        }
    }

    private fun makeCardBackground(
        fillColor: Int,
        strokeColor: Int,
        cornerDp: Int = 8
    ): GradientDrawable {
        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }
        return cardBg
    }

    private fun makeGridItemLayoutParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(12))
        }
    }

    private fun makeCreateProfileItem(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
            background = makeCardBackground(
                fillColor = Color.rgb(248, 248, 248),
                strokeColor = Color.rgb(180, 180, 180)
            )
            isClickable = true
            isFocusable = true

            layoutParams = makeGridItemLayoutParams()

            addView(TextView(context).apply {
                text = "+"
                textSize = 42f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(80, 80, 80))
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            })

            addView(TextView(context).apply {
                text = "创建新应用"
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            })

            setOnClickListener { confirmCreateProfile() }
        }
    }

    private fun confirmCreateProfile() {
        val nextProfileId = (sharedVM.getProfileIds().maxOrNull() ?: 0) + 1
        AlertDialog.Builder(this)
            .setTitle("创建新应用")
            .setMessage("是否创建 ${nextProfileId}生肖投注助手？")
            .setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val newProfileId = sharedVM.createProfile()
                enterProfile(newProfileId)
            }
            .show()
    }

    private fun confirmDeleteProfile(profileId: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除应用")
            .setMessage("是否删除 ${profileId}生肖投注助手？删除后该应用的大单、等级和开奖记录都会被清除。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                val deleted = sharedVM.deleteProfile(profileId)
                if (!deleted) {
                    toast("至少保留一个应用")
                }
            }
            .show()
    }

    private fun enterProfile(profileId: Int) {
        sharedVM.switchProfile(profileId)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        if (navController.currentDestination?.id != R.id.navigation_home) {
            navController.navigate(R.id.navigation_home)
        }

        binding.profileLauncher.visibility = View.GONE
        findViewById<View>(R.id.nav_host_fragment_activity_main).visibility = View.VISIBLE
        binding.navView.visibility = View.VISIBLE
    }

    private fun showProfileLauncher() {
        binding.profileLauncher.visibility = View.VISIBLE
        findViewById<View>(R.id.nav_host_fragment_activity_main).visibility = View.GONE
        binding.navView.visibility = View.GONE
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.profileLauncher.visibility == View.VISIBLE) {
                    finish()
                } else {
                    showProfileLauncher()
                }
            }
        })
    }

    private fun applyInsets() {
        // 1) 内容容器：吃掉状态栏高度，避免顶部重叠；底部交给约束到 nav_view 解决
        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = sys.left,
                top = sys.top,
                right = sys.right,
                bottom = 0
            )
            insets
        }

        // 2) BottomNavigation：加上手势条高度，避免按钮被底部系统手势区域挡住
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = sys.bottom)
            insets
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
