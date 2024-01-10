//
//  api.swift
//  Runner
//
//  Created by vazh2100 on 18.08.2022.
//

import Foundation
import Flutter
import TinkoffASDKCore
import TinkoffASDKUI
import UIKit

var tinkoffAcquiringChannel: FlutterMethodChannel?
var uiController: UIViewController?

func tinkoffAcquiringChannelHandler(call: FlutterMethodCall, result: @escaping FlutterResult) {
    let arguments = call.arguments as! Dictionary<String, Any>
    switch call.method {
    case "init":
        initAcquiring(
                terminalKey: arguments["terminalKey"] as! String,
                publicKey: arguments["publicKey"] as! String,
                developerMode: arguments["developerMode"] as! Bool,
                debug: arguments["debug"] as! Bool
        )
        result(nil)
    case "isTinkoffPayAvailable":
        isTinkoffPayAvailable(result: result)
    case "payWithTinkoffPay":
        payWithTinkoffPay(
                orderId: arguments["orderId"] as! String,
                description: arguments["description"] as! String,
                amountKopek: arguments["amountKopek"] as! Int64,
                itemName: arguments["itemName"] as! String,
                priceKopek: arguments["priceKopek"] as! Int64,
                tax: arguments["tax"] as! String,
                quantity: arguments["quantity"] as! Double,
                customerEmail: arguments["customerEmail"] as! String,
                taxation: arguments["taxation"] as! String,
                customerKey: arguments["customerKey"] as! String,
                tinkoffPayVersion: arguments["tinkoffPayVersion"] as! String,
                result: result
        )
    case "payWithNativeScreen":
        payWithNativeScreen(
                orderId: arguments["orderId"] as! String,
                description: arguments["description"] as! String,
                amountKopek: arguments["amountKopek"] as! Int64,
                itemName: arguments["itemName"] as! String,
                priceKopek: arguments["priceKopek"] as! Int64,
                tax: arguments["tax"] as! String,
                quantity: arguments["quantity"] as! Double,
                customerEmail: arguments["customerEmail"] as! String,
                taxation: arguments["taxation"] as! String,
                customerKey: arguments["customerKey"] as! String,
                result: result
        )
    case "launchTinkoffApp":
        launchTinkoffApp(
            deepLink: arguments["deepLink"] as! String,
            isMainAppAvailable: arguments["isMainAppAvailable"] as! Bool,
            result: result)
    case "attachCardWithNativeScreen":
        attachCardWithNativeScreen(
                customerKey: arguments["customerKey"] as! String,
                email: arguments["email"] as! String,
                result: result
        )
    default: result(FlutterMethodNotImplemented)
    }
}

private var tinkoffAcquiring: AcquiringSdk?
private var tinkoffAcquiringUI: AcquiringUISDK?

private func initAcquiring(
        terminalKey: String,
        publicKey: String,
        developerMode: Bool = false,
        debug: Bool = false
) {
    let credential = AcquiringSdkCredential(terminalKey: terminalKey, publicKey: publicKey)
    let server = developerMode ? AcquiringSdkEnvironment.test : AcquiringSdkEnvironment.prod
    let configuration = AcquiringSdkConfiguration(credential: credential, server: server)
    if (debug) {
        configuration.logger = AcquiringLoggerDefault()
    }
    do {
        try tinkoffAcquiring = AcquiringSdk.init(configuration: configuration)
        try tinkoffAcquiringUI = AcquiringUISDK.init(configuration: configuration)
        print("init", "Tinkoff Acquiring was initialized")
    } catch {
        print(error)
    }
}

private func isTinkoffPayAvailable(result: @escaping FlutterResult) {
    _ = tinkoffAcquiring?.getTinkoffPayStatus(completion: { res in
        do {
            let status: GetTinkoffPayStatusResponse.Status = try res.get().status
            switch status {
            case .allowed(let value): result([true, value.rawValue])
            case .disallowed: result([false])
            }
        } catch {
            print(error)
            result(FlutterError(code: "isTinkoffPayAvailable", message: error.localizedDescription, details: nil))
        }
    })
}

private func payWithNativeScreen(
        orderId: String,
        description: String,
        amountKopek: Int64,
        itemName: String,
        priceKopek: Int64,
        tax: String,
        quantity: Double,
        customerEmail: String,
        taxation: String,
        customerKey: String,
        result: @escaping FlutterResult
) {
    let item = Item(amount: amountKopek, price: priceKopek, name: itemName, tax: Tax.init(rawValue: tax), quantity: quantity)
    var paymentInitData = PaymentInitData(amount: amountKopek, orderId: orderId, customerKey: customerKey, payType: PayType.oneStage)
    paymentInitData.description = description
    paymentInitData.receipt = Receipt(
            shopCode: nil,
            email: customerEmail,
            taxation: Taxation.init(rawValue: taxation),
            phone: nil,
            items: [item],
            agentData: nil,
            supplierInfo: nil,
            customer: customerKey,
            customerInn: nil)

    let acquiringPaymentStageConfiguration = AcquiringPaymentStageConfiguration(paymentStage: AcquiringPaymentStageConfiguration.PaymentStage.`init`(paymentData: paymentInitData))
    let acquiringViewConfiguration = AcquiringViewConfiguration()
    acquiringViewConfiguration.featuresOptions.tinkoffPayEnabled = false
    acquiringViewConfiguration.featuresOptions.fpsEnabled = false
    acquiringViewConfiguration.fields = [
        AcquiringViewConfiguration.InfoFields.amount(
                title: NSAttributedString.init(string: description),
                amount: NSAttributedString.init(string: "\(amountKopek / 100) RUB")
        ),
    ]

    tinkoffAcquiringUI?.presentPaymentView(
            on: uiController!,
            acquiringPaymentStageConfiguration: acquiringPaymentStageConfiguration,
            configuration: acquiringViewConfiguration,
            completionHandler: { resultLocal in
                do {
                    let response = try resultLocal.get()
                    if (response.errorMessage != nil) {
                        if (response.status == .cancelled) {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                                if (uiController?.presentedViewController == nil) {
                                    result(FlutterError(code: "payWithNativeScreen", message: response.errorMessage, details: nil))
                                } else {
                                    print("is already presenting \(String(describing: uiController?.presentedViewController))")
                                }
                            }
                        } else {
                            result(FlutterError(code: "payWithNativeScreen", message: response.errorMessage, details: nil))
                        }
                    } else {
                        result([response.paymentId, response.status.rawValue])
                    }
                } catch {
                    print(error)
                    result(FlutterError(code: "payWithNativeScreen", message: error.localizedDescription, details: nil))

                }
            }
    )
}

private func payWithTinkoffPay(
        orderId: String,
        description: String,
        amountKopek: Int64,
        itemName: String,
        priceKopek: Int64,
        tax: String,
        quantity: Double,
        customerEmail: String,
        taxation: String,
        customerKey: String,
        tinkoffPayVersion: String,
        result: @escaping FlutterResult
) {
    var paymentInitData = PaymentInitData(amount: amountKopek, orderId: orderId, customerKey: customerKey, payType: PayType.oneStage)

    var agentData: AgentData? = nil
    var supplierInfo: SupplierInfo? = nil

    do {
        let siJson: String = """
                                 {
                                     "Phones": ["+74957974227"],
                                     "Name": "ЗАО «АГЕНТ.РУ»",
                                     "Inn": "7714628724"
                                 }
                             """

        let adJson: String = """
                                 {
                                     "AgentSign": "commission_agent"
                                 }
                             """

        let jsonDecoder = JSONDecoder()

        agentData = try jsonDecoder.decode(AgentData.self, from: adJson.data(using: .utf8)!)
        supplierInfo = try jsonDecoder.decode(SupplierInfo.self, from: siJson.data(using: .utf8)!)
    } catch {
        print(error)
    }

    let item =  Item(amount: amountKopek, price: priceKopek, name: itemName, tax: Tax.init(rawValue: tax), quantity: quantity, paymentObject: PaymentObject.service, paymentMethod: PaymentMethod.fullPayment, supplierInfo: supplierInfo, agentData: agentData)
    paymentInitData.description = description
    paymentInitData.receipt = Receipt(
            shopCode: nil, email: customerEmail, taxation: Taxation.init(rawValue: taxation), phone: nil, items: [item],
            agentData: nil, supplierInfo: nil, customer: customerKey, customerInn: nil)
    paymentInitData.paymentFormData = ["TinkoffPayWeb":"true"]

    _ = tinkoffAcquiring?.paymentInit(data: paymentInitData, completionHandler: { (resultint: Result<PaymentInitResponse, Error>) -> () in
        do {
            let paymentId = try resultint.get().paymentId
            print("payWithTinkoff", paymentId)
            getDeepLink(paymentId: paymentId, tinkoffPayVersion: tinkoffPayVersion, result: result)


        } catch {
            print(error)
            result(FlutterError(code: "payWithTinkoff", message: error.localizedDescription, details: nil))
        }
    })
}


private func getDeepLink(
        paymentId: Int64,
        tinkoffPayVersion: String,
        result: @escaping FlutterResult
) {
    _ = tinkoffAcquiring?.getTinkoffPayLink(paymentId: paymentId, version: GetTinkoffPayStatusResponse.Status.Version(rawValue: tinkoffPayVersion)!, completion: {
        resultLocal in
        do {
            let tinkoffAppUrl = try resultLocal.get().redirectUrl
            print("getDeepLink", tinkoffAppUrl)
            result([paymentId, tinkoffAppUrl.absoluteString])
        } catch {
            print(error)
            result(FlutterError(code: "getDeepLink", message: error.localizedDescription, details: nil))
        }
    })
}


private func launchTinkoffApp(
        deepLink: String,
        isMainAppAvailable: Bool,
        result: @escaping FlutterResult
) {
    let tinkoffBankOnelink = "https://tinkoffbank.onelink.me"
    let iosParam = "ios_url="
    let docStorageParam = "af_dp="
    var iosLink = deepLink
    var docStorageLink = deepLink
    if deepLink.hasPrefix(tinkoffBankOnelink) {
        let parameters = deepLink.components(separatedBy: "&")
        for param in parameters {
            if param.contains(iosParam) {
                if let index = param.range(of: iosParam)?.upperBound {
                   iosLink = String(param.suffix(from: index))
                }
            }
            if param.contains(docStorageParam) {
                if let index = param.range(of: docStorageParam)?.upperBound {
                   docStorageLink = String(param.suffix(from: index))
                }
            }
        }
    }

    if (!isMainAppAvailable){
        if let docURL = URL(string: docStorageLink) {
                   UIApplication.shared.open(docURL, options: [:]) { success in
                       if !success {
                          result(FlutterError(code: "launchTinkoffApp", message: "Error opening the Docstorage App", details: nil))
                       }
                   }
        }
    } else {
        if let iosURL = URL(string: iosLink) {
            UIApplication.shared.open(iosURL, options: [:]) { success in
                        if !success {
                           result(FlutterError(code: "launchTinkoffApp", message: "Error opening the Tinkoff App", details: nil))
                        }
                    }
        }
    }
}

private func attachCardWithNativeScreen(customerKey: String, email: String, result: @escaping FlutterResult) {
    let configuration = AcquiringViewConfiguration()

    tinkoffAcquiringUI?.addCardNeedSetCheckTypeHandler = {
        PaymentCardCheckType.hold3DS
    }

    tinkoffAcquiringUI?.presentAddCardView(on: uiController!, customerKey: customerKey, configuration: configuration, completeHandler: { res in
        do {
            let paymentCard = try res.get()
            if (paymentCard == nil) {
                result(FlutterError(code: "attachCardWithNativeScreen", message: "Привязка карты отменена пользователем", details: nil))
            } else {
                result([paymentCard!.cardId])
            }
        } catch {
            result(FlutterError(code: "attachCardWithNativeScreen", message: error.localizedDescription, details: nil))
        }
    })
}
