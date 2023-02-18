import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:tinkoff_acquiring_native_flutter/tinkoff_acquiring_native_flutter.dart';
import 'package:tinkoff_acquiring_native_flutter/tinkoff_acquiring_native_flutter_platform_interface.dart';

class MockTinkoffAcquiringNativeFlutterPlatform
    with MockPlatformInterfaceMixin
    implements TinkoffAcquiringNativeFlutterPlatform {}

void main() {
  final TinkoffAcquiringNativeFlutterPlatform initialPlatform =
      TinkoffAcquiringNativeFlutterPlatform.instance;

  test('$TinkoffAcquiring is the default instance', () {
    expect(initialPlatform, isInstanceOf<TinkoffAcquiring>());
  });
}
