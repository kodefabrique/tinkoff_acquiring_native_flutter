package com.kodefabrique.tinkoff_acquiring_native_flutter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.tinkoff.acquiring.sdk.AcquiringSdk
import ru.tinkoff.acquiring.sdk.TinkoffAcquiring
import ru.tinkoff.acquiring.sdk.models.*
import ru.tinkoff.acquiring.sdk.models.enums.*
import ru.tinkoff.acquiring.sdk.models.options.CustomerOptions
import ru.tinkoff.acquiring.sdk.models.options.FeaturesOptions
import ru.tinkoff.acquiring.sdk.models.options.screen.PaymentOptions
import ru.tinkoff.acquiring.sdk.payment.TpayPaymentState
import ru.tinkoff.acquiring.sdk.payment.TpayProcess
import ru.tinkoff.acquiring.sdk.redesign.cards.attach.AttachCardLauncher
import ru.tinkoff.acquiring.sdk.redesign.mainform.MainFormLauncher
import ru.tinkoff.acquiring.sdk.redesign.tpay.models.enableTinkoffPay
import ru.tinkoff.acquiring.sdk.redesign.tpay.models.getTinkoffPayVersion
import ru.tinkoff.acquiring.sdk.utils.Money
import ru.tinkoff.acquiring.sdk.utils.SampleAcquiringTokenGenerator


class Api {
    var activity: Activity? = null

    lateinit var tinkoffAcquiringChannel: MethodChannel
    lateinit var tinkoffAcquiringChannelBackground: MethodChannel
    var payWithNativeScreenResult: MethodChannel.Result? = null
    var attachCardWithNativeScreenResult: MethodChannel.Result? = null

    private lateinit var tinkoffAcquiring: TinkoffAcquiring

    val REQUEST_CODE_MAIN_FORM = 100
    val REQUEST_CODE_ATTACH = 200
    val scope = CoroutineScope(Dispatchers.Main)

    fun tinkoffAcquiringChannelHandler(call: MethodCall, result: MethodChannel.Result) {
        val arguments = call.arguments as HashMap<*, *>
        when (call.method) {
            "init" -> {
                init(
                    terminalKey = arguments["terminalKey"] as String,
                    publicKey = arguments["publicKey"] as String,
                    terminalSecret = arguments["terminalSecret"] as String,
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
                    terminalKey = arguments["terminalKey"] as String,
                    publicKey = arguments["publicKey"] as String,
                    successUrl = arguments["successUrl"] as String,
                    failUrl = arguments["failUrl"] as String,
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
                    terminalKey = arguments["terminalKey"] as String,
                    publicKey = arguments["publicKey"] as String,
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
        terminalSecret: String,
        publicKey: String,
        developerMode: Boolean = false,
        debug: Boolean = false
    ) {
        AcquiringSdk.isDebug = debug
        AcquiringSdk.isDeveloperMode = developerMode
        AcquiringSdk.tokenGenerator = SampleAcquiringTokenGenerator(terminalSecret)
        tinkoffAcquiring = TinkoffAcquiring(activity!!.applicationContext, terminalKey, publicKey)
        tinkoffAcquiring.initTinkoffPayPaymentSession()
        Log.d("init", "Tinkoff Acquiring was initialized")
    }


    private fun isTinkoffPayAvailable(result: MethodChannel.Result) {
        tinkoffAcquiring.checkTerminalInfo(
            onSuccess = {
                Log.d("isTinkoffPayAvailable", "success")
                result.success(listOf(it.enableTinkoffPay(), it?.getTinkoffPayVersion()))
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
        terminalKey: String,
        publicKey: String,
        successUrl: String,
        failUrl: String,
        result: MethodChannel.Result
    ) {
        val paymentOptions = PaymentOptions().setOptions {
            setTerminalParams(terminalKey, publicKey)
            orderOptions {
                this.successURL = successUrl
                this.failURL = failUrl
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
        }

        val process = TpayProcess.get()
        process?.start(paymentOptions, tinkoffPayVersion)
        scope.launch {
            process?.state?.collect {
                when (it) {
                    is TpayPaymentState.Started -> {
                        Log.d("payWithTinkoff", "Started")
                    }

                    is TpayPaymentState.Created -> {
                        Log.d("payWithTinkoff", "Created")
                    }

                    is TpayPaymentState.NeedChooseOnUi -> {
                        Log.d("payWithTinkoff", "NeedChooseOnUi")
                        result.success(listOf(it.paymentId, it.deeplink))
                    }

                    is TpayPaymentState.LeaveOnBankApp -> {
                        Log.d("payWithTinkoff", "LeaveOnBankApp")
                    }

                    is TpayPaymentState.CheckingStatus -> {
                        Log.d("payWithTinkoff", "CheckingStatus")
                    }

                    is TpayPaymentState.PaymentFailed -> {
                        Log.d("payWithTinkoff", "error", it.throwable)
                        result.error(
                            "payWithTinkoff",
                            it.throwable.message,
                            it.throwable.stackTraceToString()
                        )
                    }

                    is TpayPaymentState.Success -> {
                        Log.d("payWithTinkoff", "payment status is Success")
                    }

                    is TpayPaymentState.Stopped -> {
                        Log.d("payWithTinkoff", "Stopped")
                    }
                }
            }
        }
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
        terminalKey: String,
        publicKey: String,
        result: MethodChannel.Result,
    ) {
        val paymentOptions = PaymentOptions().setOptions {
            setTerminalParams(terminalKey, publicKey)
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
        }
        val startData = MainFormLauncher.StartData(paymentOptions)
        activity?.let {
            val intent = MainFormLauncher.Contract.createIntent(it, startData)
            it.startActivityForResult(intent, REQUEST_CODE_MAIN_FORM)
        }
        payWithNativeScreenResult = result
    }

    private fun attachCardWithNativeScreen(
        customerKey: String,
        email: String,
        result: MethodChannel.Result
    ) {
        val attachCardOptions = tinkoffAcquiring.attachCardOptions {}
        val customer = CustomerOptions().apply {
            this.customerKey = customerKey// уникальный ID пользователя для сохранения данных его карты
            checkType = CheckType.THREE_DS_HOLD.toString()// тип привязки карты
            this.email = email// E-mail клиента для отправки уведомления о привязке
        }
        attachCardOptions.customer = customer
        val featuresOptions = FeaturesOptions().apply {
            useSecureKeyboard = true
        }
        attachCardOptions.features = featuresOptions

        activity?.let {
            val intent = AttachCardLauncher.Contract.createIntent(it, attachCardOptions)
            it.startActivityForResult(intent, REQUEST_CODE_ATTACH)
        }
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


