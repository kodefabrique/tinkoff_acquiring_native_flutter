package com.kodefabrique.tinkoff_acquiring_native_flutter

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.StandardMethodCodec
import ru.tinkoff.acquiring.sdk.redesign.common.LauncherConstants

/** TinkoffAcquiringNativeFlutterPlugin */
class TinkoffAcquiringNativeFlutterPlugin : FlutterPlugin, ActivityAware, ActivityResultListener {

    private val api: Api = Api()

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        api.tinkoffAcquiringChannel =
            MethodChannel(flutterPluginBinding.binaryMessenger, "tinkoff_acquiring_native_flutter")
        api.tinkoffAcquiringChannel.setMethodCallHandler(api::tinkoffAcquiringChannelHandler)

        val taskQueue = flutterPluginBinding.binaryMessenger.makeBackgroundTaskQueue()
        api.tinkoffAcquiringChannelBackground = MethodChannel(
            flutterPluginBinding.binaryMessenger,
            "tinkoff_acquiring_native_flutter_background",
            StandardMethodCodec.INSTANCE,
            taskQueue
        )

        api.tinkoffAcquiringChannelBackground.setMethodCallHandler(api::tinkoffAcquiringBackgroundChannelHandler)
    }


    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        api.tinkoffAcquiringChannel.setMethodCallHandler(null)
        api.tinkoffAcquiringChannelBackground.setMethodCallHandler(null)
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        api.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        api.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        api.activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        api.activity = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        ///Получение результата оплаты
        if (requestCode == api.REQUEST_CODE_MAIN_FORM) {
            Log.d("onActivityResult", "resultCode $resultCode")
            when (resultCode) {
                RESULT_OK -> {
                    val paymentId = data?.getLongExtra(LauncherConstants.EXTRA_PAYMENT_ID, 0)
                    api.payWithNativeScreenResult?.success(listOf(paymentId, "CONFIRMED"))
                    return true
                }

                RESULT_CANCELED -> {
                    api.payWithNativeScreenResult?.success(listOf(0, "CANCELLED"))
                    return true
                }

                else -> {
                    val error =
                        data?.getSerializableExtra(LauncherConstants.EXTRA_ERROR) as Throwable
                    api.payWithNativeScreenResult?.error(
                        "nativeScreenResult",
                        error.message,
                        error.stackTraceToString()
                    )
                    return true;
                }
            }
            api.payWithNativeScreenResult = null
        }
        ///Получение результата привязки карты
        if (requestCode == api.REQUEST_CODE_ATTACH) {
            when (resultCode) {
                RESULT_OK -> {
                    val cardId = data!!.getStringExtra(LauncherConstants.EXTRA_CARD_ID)
                    Log.d(
                        "rebillId",
                        data.getStringExtra(LauncherConstants.EXTRA_REBILL_ID).toString()
                    )
                    api.attachCardWithNativeScreenResult?.success(arrayListOf(cardId))
                    return true
                }
                LauncherConstants.RESULT_ERROR -> {
                    val error =
                        data?.getSerializableExtra(LauncherConstants.EXTRA_ERROR) as Throwable
                    Log.d("ERROR_TAG", data.toString())
                    api.attachCardWithNativeScreenResult?.error(
                        "attachScreenResult",
                        error.message,
                        error.stackTraceToString()
                    )
                    return true
                }
                RESULT_CANCELED -> {
                    api.attachCardWithNativeScreenResult?.error(
                        "attachScreenResult",
                        "Привязка карты отменена пользователем",
                        "Детали"
                    )
                    return true
                }
            }
            api.attachCardWithNativeScreenResult = null
        }
        return false
    }

}
