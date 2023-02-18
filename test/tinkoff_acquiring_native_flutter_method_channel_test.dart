import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tinkoff_acquiring_native_flutter/tinkoff_acquiring_native_flutter.dart';

void main() {
  TinkoffAcquiring platform = TinkoffAcquiring();
  const MethodChannel channel =
      MethodChannel('tinkoff_acquiring_native_flutter');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });
}
