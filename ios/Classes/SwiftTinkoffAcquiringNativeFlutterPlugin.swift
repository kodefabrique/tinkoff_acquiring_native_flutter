import Flutter
import UIKit

public class SwiftTinkoffAcquiringNativeFlutterPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
      tinkoffAcquiringChannel = FlutterMethodChannel(name: "tinkoff_acquiring_native_flutter", binaryMessenger: registrar.messenger())
      uiController = (UIApplication.shared.delegate?.window??.rootViewController)!
      tinkoffAcquiringChannel?.setMethodCallHandler(tinkoffAcquiringChannelHandler)
  }
}
