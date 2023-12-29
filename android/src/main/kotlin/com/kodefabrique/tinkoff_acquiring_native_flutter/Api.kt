package com.kodefabrique.tinkoff_acquiring_native_flutter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import ru.tinkoff.acquiring.sdk.AcquiringSdk
import ru.tinkoff.acquiring.sdk.TinkoffAcquiring
import ru.tinkoff.acquiring.sdk.models.*
import ru.tinkoff.acquiring.sdk.models.enums.*
import ru.tinkoff.acquiring.sdk.models.options.screen.AttachCardOptions
import ru.tinkoff.acquiring.sdk.models.options.screen.PaymentOptions
import ru.tinkoff.acquiring.sdk.payment.PaymentListener
import ru.tinkoff.acquiring.sdk.payment.PaymentProcess
import ru.tinkoff.acquiring.sdk.payment.PaymentState
import ru.tinkoff.acquiring.sdk.utils.Money


class Api {
    var activity: Activity? = null

    lateinit var tinkoffAcquiringChannel: MethodChannel
    lateinit var tinkoffAcquiringChannelBackground: MethodChannel
    var payWithNativeScreenResult: MethodChannel.Result? = null
    var attachCardWithNativeScreenResult: MethodChannel.Result? = null

    private lateinit var tinkoffAcquiring: TinkoffAcquiring

    fun tinkoffAcquiringChannelHandler(call: MethodCall, result: MethodChannel.Result) {
        val arguments = call.arguments as HashMap<*, *>
        when (call.method) {
            "init" -> {
                init(
                    terminalKey = arguments["terminalKey"] as String,
                    publicKey = arguments["publicKey"] as String,
                    developerMode = arguments["developerMode"] as Boolean,
                    debug = arguments["debug"] as Boolean,
                )
                result.success(null)
            }
            "isTinkoffPayAvailable" -> {
                isTinkoffPayAvailable(result)
            }
            "payWithTinkoffPay" -> {
                payWithTinkoffPay(
                    orderId = arguments["orderId"] as String,
                    description = arguments["description"] as String,
                    amountKopek = if (arguments["amountKopek"] is Integer) (arguments["amountKopek"] as Integer).toLong() else arguments["amountKopek"] as Long,
                    itemName = arguments["itemName"] as String,
                    priceKopek = if (arguments["priceKopek"] is Integer) (arguments["priceKopek"] as Integer).toLong() else arguments["priceKopek"] as Long,
                    tax = arguments["tax"] as String,
                    quantity = arguments["quantity"] as Double,
                    customerEmail = arguments["customerEmail"] as String,
                    taxation = arguments["taxation"] as String,
                    customerKey = arguments["customerKey"] as String,
                    tinkoffPayVersion = arguments["tinkoffPayVersion"] as String,
                    result
                )
            }
            "launchTinkoffApp" -> {
                launchTinkoffApp(
                    deepLink = arguments["deepLink"] as String, activity = activity!!
                )
                result.success(null)
            }
            "payWithNativeScreen" -> {
                payWithNativeScreen(
                    orderId = arguments["orderId"] as String,
                    description = arguments["description"] as String,
                    amountKopek = if (arguments["amountKopek"] is Integer) (arguments["amountKopek"] as Integer).toLong() else arguments["amountKopek"] as Long,
                    itemName = arguments["itemName"] as String,
                    priceKopek = if (arguments["priceKopek"] is Integer) (arguments["priceKopek"] as Integer).toLong() else arguments["priceKopek"] as Long,
                    tax = arguments["tax"] as String,
                    quantity = arguments["quantity"] as Double,
                    customerEmail = arguments["customerEmail"] as String,
                    taxation = arguments["taxation"] as String,
                    customerKey = arguments["customerKey"] as String,
                    activeCardId = arguments["activeCardId"] as String,
                    recurrentPayment = arguments["recurrentPayment"] as Boolean,
                    result
                )
            }
            "attachCardWithNativeScreen" -> {
                attachCardWithNativeScreen(
                    customerKey = arguments["customerKey"] as String,
                    email = arguments["email"] as String,
                    result
                )
            }
            else -> result.notImplemented()
        }
    }

    fun tinkoffAcquiringBackgroundChannelHandler(call: MethodCall, result: MethodChannel.Result) {
        val arguments = call.arguments as HashMap<*, *>
        when (call.method) {
            "checkPaymentStatus" -> {
                checkPaymentStatus(
                    paymentId = if (arguments["paymentId"] is Integer) (arguments["paymentId"] as Integer).toLong() else arguments["paymentId"] as Long,
                    result
                )
            }
            else -> result.notImplemented()
        }
    }


    private fun init(
        terminalKey: String,
        publicKey: String,
        developerMode: Boolean = false,
        debug: Boolean = false
    ) {
        AcquiringSdk.isDebug = debug
        AcquiringSdk.isDeveloperMode = developerMode
        tinkoffAcquiring = TinkoffAcquiring(activity!!.applicationContext, terminalKey, publicKey)
        Log.d("init", "Tinkoff Acquiring was initialized")
    }


    private fun isTinkoffPayAvailable(result: MethodChannel.Result) {
        tinkoffAcquiring.checkTinkoffPayStatus(
            onSuccess = {
                Log.d("isTinkoffPayAvailable", "success")
                result.success(listOf(it.isTinkoffPayAvailable(), it.getTinkoffPayVersion()))
            },
            onFailure = {
                Log.d("isTinkoffPayAvailable", "failure")
                result.error("isTinkoffPayAvailable", it.message, it.stackTraceToString())
            },
        )
    }

    private fun payWithTinkoffPay(
        orderId: String,
        description: String,
        amountKopek: Long,
        itemName: String,
        priceKopek: Long,
        tax: String,
        quantity: Double,
        customerEmail: String,
        taxation: String,
        customerKey: String,
        tinkoffPayVersion: String,
        result: MethodChannel.Result
    ) {
        val process: PaymentProcess = tinkoffAcquiring.payWithTinkoffPay(
            PaymentOptions().setOptions {
                orderOptions {
                    this.orderId = orderId
                    this.description = description
                    this.amount = Money.ofCoins(amountKopek)
                    this.receipt = Receipt(
                        items = arrayListOf(Item(
                            name = itemName,
                            price = priceKopek,
                            quantity = quantity,
                            amount = amountKopek,
                            tax = Tax.valueOf(tax),
                        ).apply {
                            paymentMethod = PaymentMethod.FULL_PAYMENT
                            paymentObject = PaymentObject.SERVICE
                            agentData = AgentData().apply { agentSign = AgentSign.COMMISSION_AGENT }
                            supplierInfo = SupplierInfo().apply {
                                phones = arrayOf("+74957974227")
                                name = "ЗАО «АГЕНТ.РУ»"
                                inn = "7714628724"
                            }
                        }), email = customerEmail, taxation = Taxation.valueOf(taxation)
                    )
                }
                customerOptions {
                    this.customerKey = customerKey
                    this.email = customerEmail
                }
                featuresOptions {
                    this.tinkoffPayEnabled = true
                }
            }, version = tinkoffPayVersion
        ).subscribe(object : PaymentListener {
            override fun onError(throwable: Throwable, paymentId: Long?) {
                Log.d("payWithTinkoff", "error", throwable)
                result.error("payWithTinkoff", throwable.message, throwable.stackTraceToString())
            }

            override fun onStatusChanged(state: PaymentState?) {
                if (state != null) {
                    Log.d("onStatusChanged", state.name)
                }
            }

            override fun onSuccess(paymentId: Long, cardId: String?, rebillId: String?) {
                Log.d("payWithTinkoff", "payment status is Success")
            }

            override fun onUiNeeded(state: AsdkState) {
                if (state is OpenTinkoffPayBankState) {
                    result.success(listOf(state.paymentId, state.deepLink))
                }
            }
        }).start()
    }

    private fun launchTinkoffApp(deepLink: String, activity: Activity) {
        val tinkoffBankOnelink = "https://tinkoffbank.onelink.me"
        val andoidParam = "android_url="
        var androidLink = deepLink
        if (deepLink.startsWith(tinkoffBankOnelink)) {
            val parameters = deepLink.split("&")
            for (param in parameters) {
                if (param.contains(andoidParam)) {
                    androidLink = param.substringAfter(andoidParam)
                }
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(androidLink))
        val tinkoffPackages =
            activity.applicationContext.packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName.contains("tinkoff") }.map {
                    Intent(
                        intent.action, intent.data
                    ).setPackage(it.activityInfo.packageName)
                }
        if (tinkoffPackages.isNotEmpty()) {
            val chooserIntent = Intent.createChooser(tinkoffPackages[0], "Open with Tinkoff")

            if (tinkoffPackages.size > 1) {
                val newList = tinkoffPackages.subList(1, tinkoffPackages.size).toTypedArray()
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, newList)
            }
            activity.startActivity(chooserIntent)
        }
    }


    private fun payWithNativeScreen(
        orderId: String,
        description: String,
        amountKopek: Long,
        itemName: String,
        priceKopek: Long,
        tax: String,
        quantity: Double,
        customerEmail: String,
        taxation: String,
        customerKey: String,
        activeCardId: String,
        recurrentPayment: Boolean,
        result: MethodChannel.Result,
    ) {
        tinkoffAcquiring.openPaymentScreen(
            activity = activity!!,
            options = PaymentOptions().setOptions {
                orderOptions {
                    this.orderId = orderId
                    this.description = description
                    this.amount = Money.ofCoins(amountKopek)
                    this.recurrentPayment = recurrentPayment
                    this.receipt = Receipt(
                        items = arrayListOf(Item(
                            name = itemName,
                            price = priceKopek,
                            quantity = quantity,
                            amount = amountKopek,
                            tax = Tax.valueOf(tax),
                        ).apply {
                            paymentMethod = PaymentMethod.FULL_PAYMENT
                            paymentObject = PaymentObject.SERVICE
                        }), email = customerEmail, taxation = Taxation.valueOf(taxation)
                    )
                }
                customerOptions {
                    this.customerKey = customerKey
                    this.email = customerEmail
                    this.checkType = CheckType.NO.name
                }
                featuresOptions {
                    this.tinkoffPayEnabled = false
                    this.userCanSelectCard = false
                    this.selectedCardId = activeCardId
                }
            },
            requestCode = 100,
        )
        payWithNativeScreenResult = result
    }

    private fun attachCardWithNativeScreen(
        customerKey: String, email: String, result: MethodChannel.Result
    ) {
        tinkoffAcquiring.openAttachCardScreen(
            activity = activity!!, options = AttachCardOptions().setOptions {
                customerOptions {                       // данные покупателя
                    this.customerKey =
                        customerKey        // уникальный ID пользователя для сохранения данных его карты
                    this.checkType = CheckType.THREE_DS_HOLD.toString() // тип привязки карты
                    this.email = email // E-mail клиента для отправки уведомления о привязке
                }
                featuresOptions { // настройки визуального отображения и функций экрана оплаты
                    this.useSecureKeyboard = true
                }
            }, requestCode = 200
        )
        attachCardWithNativeScreenResult = result
    }

    private fun checkPaymentStatus(paymentId: Long, result: MethodChannel.Result) {
        tinkoffAcquiring.sdk.getState {
            this.paymentId = paymentId
        }.execute(
            onSuccess = {
                activity!!.runOnUiThread {
                    result.success(it.status.toString())
                }
            },
            onFailure = {
                activity!!.runOnUiThread {
                    result.error("checkPaymentStatus", it.message, it.stackTraceToString())
                }
            },
        )
    }


}


