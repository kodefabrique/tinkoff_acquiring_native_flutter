import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'tinkoff_acquiring_native_flutter.dart';

abstract class TinkoffAcquiringNativeFlutterPlatform extends PlatformInterface {
  /// Constructs a TinkoffAcquiringNativeFlutterPlatform.
  TinkoffAcquiringNativeFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static TinkoffAcquiringNativeFlutterPlatform _instance = TinkoffAcquiring();

  /// The default instance of [TinkoffAcquiringNativeFlutterPlatform] to use.
  ///
  /// Defaults to [TinkoffAcquiring].
  static TinkoffAcquiringNativeFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [TinkoffAcquiringNativeFlutterPlatform] when
  /// they register themselves.
  static set instance(TinkoffAcquiringNativeFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

}
