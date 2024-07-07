import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class LatLng {
  LatLng(this.latitude, this.longitude);
  final double latitude;
  final double longitude;
}

typedef OnLocationUpdated = void Function(LatLng location);

/// A service used to communicate with the native platform to start and stop the foreground service.
///
/// Applies only to Android as of now.
class ForegroundService extends ChangeNotifier {
  static const MethodChannel _channel = MethodChannel('foreground_service');

  bool connected = false;

  static const EventChannel _locationChannel =
      EventChannel('foreground_service_location');

  Future<void> startOrConnect() async {
    await _channel.invokeMethod('start');
    connected = true;
    notifyListeners();
  }

  Future<void> stop() async {
    await _channel.invokeMethod('stop');
    connected = false;
    notifyListeners();
  }

  Stream<String> location() {
    return _locationChannel.receiveBroadcastStream().map((event) {
      final location = event;
      return location;
    });
  }
}
