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
                terminalSecret: arguments["terminalSecret"] as! String,
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
                successUrl: arguments["successUrl"] as! String,
                failUrl: arguments["failUrl"] as! String,
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
        terminalSecret: String,
        publicKey: String,
        developerMode: Bool = false,
        debug: Bool = false
) {
    let credential = AcquiringSdkCredential(terminalKey: terminalKey, publicKey: publicKey)
    let server = developerMode ? AcquiringSdkEnvironment.test : AcquiringSdkEnvironment.prod
    let tokenProvider = TokenProvider(terminalSecret: terminalSecret)
    let configuration = AcquiringSdkConfiguration(
        credential: credential, server: server, logger: debug ? Logger() : nil,
        tokenProvider: tokenProvider
    )

    do {
        try tinkoffAcquiring = AcquiringSdk.init(configuration: configuration)
        try tinkoffAcquiringUI = AcquiringUISDK.init(coreSDKConfiguration: configuration, uiSDKConfiguration: UISDKConfiguration(addCardCheckType:  PaymentCardCheckType.hold3DS))
        print("init", "Tinkoff Acquiring was initialized")
    } catch {
        print(error)
    }
}

private func isTinkoffPayAvailable(result: @escaping FlutterResult) {
   _ = tinkoffAcquiring?.getTinkoffPayStatus(completion: { res in
        do{
            let status: GetTinkoffPayStatusPayload.Status = try res.get().status
            switch status{
            case .allowed(let value): result([true, value.version])
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

    let item = Item_v1_05(
        amount: amountKopek,
        price: priceKopek,
        name: itemName,
        tax: Tax(rawValue: tax),
        quantity: quantity
    )

    var receipt: Receipt?

    do {
        receipt = try Receipt.version1_05(
            ReceiptFdv1_05(
                shopCode: nil,
                email: customerEmail,
                taxation: Taxation.init(rawValue: taxation),
                phone: nil,
                items: [item],
                agentData: nil,
                supplierInfo: nil
                //customer: customerKey
            )
        )
    } catch {
      result(FlutterError(code: "attachCardWithNativeScreen", message: error.localizedDescription, details: nil))
    }

    var orderOptions = OrderOptions(
        orderId: orderId,
        amount: amountKopek,
        description: description,
        payType: PayType.oneStage,
        receipt: receipt
    )

    tinkoffAcquiringUI?.presentTinkoffPay(
        on: uiController!,
        paymentFlow: PaymentFlow.full(
            paymentOptions: PaymentOptions(
                orderOptions: orderOptions, customerOptions: CustomerOptions(customerKey: customerKey, email: customerEmail)
            )
        ),
        completion: { resultLocal in
            do {
                let resultLocal: PaymentResult = resultLocal

                switch resultLocal {
                case .succeeded(let payment):
                    result([payment.paymentId])
                case .failed(let error):
                    result(FlutterError(code: "payWithNativeScreen", message: error.localizedDescription, details: nil))
                case .cancelled(let cancel):
                    result(FlutterError(code: "payWithNativeScreen", message: "\(cancel)", details: nil))
                }
            } catch {
                result(FlutterError(code: "attachCardWithNativeScreen", message: error.localizedDescription, details: nil))
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
    successUrl: String,
    failUrl: String,
    result: @escaping FlutterResult
) {

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

    let receipt: Receipt?

    do{
        receipt = try Receipt.version1_05(
            ReceiptFdv1_05(
                shopCode: nil,
                email: customerEmail,
                taxation: Taxation(rawValue: taxation),
                phone: nil,
                items: [
                    Item_v1_05(
                        amount: amountKopek,
                        price: priceKopek,
                        name: itemName,
                        tax: Tax(rawValue: tax),
                        quantity: quantity,
                        paymentObject: PaymentObject_v1_05.service,
                        paymentMethod: PaymentMethod.fullPayment,
                        supplierInfo: supplierInfo,
                        agentData: agentData
                    )
                ],
                agentData: nil,
                supplierInfo: nil
            )
        )


        var paymentInitData = PaymentInitData(
            amount: amountKopek,
            orderId: orderId,
            customerKey: customerKey,
            payType: PayType.oneStage
        )

        paymentInitData.description = description
        paymentInitData.receipt = receipt
        paymentInitData.successURL = successUrl
        paymentInitData.failURL = failUrl
        paymentInitData.additionalData = AdditionalData(
            data: ["TinkoffPayWeb": "true"]
        )

        _ = tinkoffAcquiring?.initPayment(data: paymentInitData, completion: { (resultint: Result<InitPayload, Error>) -> () in
            do {
                let paymentId = try resultint.get().paymentId
                print("payWithTinkoff", paymentId)
                getDeepLink(paymentId: "\(paymentId)", tinkoffPayVersion: tinkoffPayVersion, result: result)
            } catch {
                print(error)
                result(FlutterError(code: "payWithTinkoff", message: error.localizedDescription, details: nil))
            }
        })
    } catch {
      result(FlutterError(code: "payWithTinkoff", message: error.localizedDescription, details: nil))
    }
}

private func getDeepLink(
        paymentId: String,
        tinkoffPayVersion: String,
        result: @escaping FlutterResult
) {
    _ = tinkoffAcquiring?.getTinkoffPayLink(
        data: GetTinkoffLinkData(paymentId: "\(paymentId)", version: tinkoffPayVersion),
        completion: { resultLocal in
            do {
                let tinkoffAppUrl = try resultLocal.get().redirectUrl
                print("getDeepLink", tinkoffAppUrl)
                result([paymentId, tinkoffAppUrl.absoluteString])
            } catch {
                print(error)
                result(FlutterError(code: "getDeepLink", message: error.localizedDescription, details: nil))
            }
        }
    )
}

private func openURL(_ url: URL, errorMessage: String, result: @escaping FlutterResult) {
    UIApplication.shared.open(url, options: [:]) { success in
        if success {
            result(["openedSuccessfully": true])
        } else {
            result(FlutterError(code: "launchTinkoffApp", message: errorMessage, details: nil))
        }
    }
}

private func launchTinkoffApp(deepLink: String, isMainAppAvailable: Bool, result: @escaping FlutterResult) {
    let tinkoffBankOnelink = "https://tinkoffbank.onelink.me"
    let iosParam = "ios_url="
    let docStorageParam = "af_dp="
    var iosLink = deepLink
    var docStorageLink = ""

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

        if !isMainAppAvailable {
            if let docURL = URL(string: docStorageLink) {
                openURL(docURL, errorMessage: "Error opening the Docstorage App", result: result)
            }
        } else {
            if let iosURL = URL(string: iosLink) {
                openURL(iosURL, errorMessage: "Error opening the Tinkoff App", result: result)
            }
        }
    } else {
        if !isMainAppAvailable {
            result(FlutterError(code: "launchTinkoffApp", message: "Error opening the Tinkoff App", details: nil))
        } else {
            if let iosURL = URL(string: iosLink) {
                openURL(iosURL, errorMessage: "Error opening the Tinkoff App", result: result)
            }
        }
    }
}

private func attachCardWithNativeScreen(customerKey: String, email: String, result: @escaping FlutterResult) {
    var options = AddCardOptions(attachCardData: nil)
    tinkoffAcquiringUI?.presentAddCard(
        on: uiController!,
        customerKey: customerKey,
        addCardOptions: options,
        completion: { res in
            do {
                let res: AddCardResult = res
                switch res {
                case .succeded(let cards):
                    result([cards.cardId])
                case .cancelled:
                    result(FlutterError(code: "attachCardWithNativeScreen", message: "Привязка карты отменена пользователем", details: nil))
                case .failed(let error):
                    result(FlutterError(code: "attachCardWithNativeScreen", message: error.localizedDescription, details: nil))
                }
            } catch {
                result(FlutterError(code: "attachCardWithNativeScreen", message: error.localizedDescription, details: nil))
            }
        }
    )
}

import CommonCrypto
import Foundation
import TinkoffASDKCore

class TokenProvider: ITokenProvider {

    let terminalSecret: String

    init(terminalSecret: String) {
           self.terminalSecret = terminalSecret
       }
    func provideToken(
        forRequestParameters parameters: [String: String],
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        let sourceString = parameters
            .merging(["Password": terminalSecret]) { $1 }
            .sorted { $0.key < $1.key }
            .map(\.value)
            .joined()

        let hashingResult = Result { try SHA256.hash(from: sourceString) }
        completion(hashingResult)
    }
}

enum SHA256 {
    private enum Error: Swift.Error {
        case failedToHash(input: String)
    }

    static func hash(from string: String) throws -> String {
        guard let data = string.data(using: .utf8) else {
            throw Error.failedToHash(input: string)
        }
        return hexString(from: digest(input: data as NSData))
    }

    private static func hexString(from data: NSData) -> String {
        var bytes = [UInt8](repeating: 0, count: data.length)
        data.getBytes(&bytes, length: data.length)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    private static func digest(input: NSData) -> NSData {
        let digestLength = Int(CC_SHA256_DIGEST_LENGTH)
        var hash = [UInt8](repeating: 0, count: digestLength)
        CC_SHA256(input.bytes, UInt32(input.length), &hash)
        return NSData(bytes: hash, length: digestLength)
    }
}
