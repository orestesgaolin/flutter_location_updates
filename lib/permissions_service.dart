import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

class PermissionsService extends ChangeNotifier {
  bool permissionsGranted = false;
  bool notificationsGranted = false;
  PermissionStatus? locationStatus;
  PermissionStatus? notificationStatus;

  Future<void> checkLocationPermissions() async {
    final response = await Permission.location.status;
    locationStatus = response;

    if (response.isGranted) {
      permissionsGranted = true;
    } else {
      permissionsGranted = false;
    }
    notifyListeners();
  }

  Future<void> askForLocationPermissions() async {
    final response = await Permission.location.request();
    locationStatus = response;

    if (response.isGranted) {
      permissionsGranted = true;
    } else {
      permissionsGranted = false;
    }
    notifyListeners();
  }

  Future<void> checkNotificationPermissions() async {
    final response = await Permission.notification.status;
    notificationStatus = response;

    if (response.isGranted) {
      notificationsGranted = true;
    } else {
      notificationsGranted = false;
    }
    notifyListeners();
  }

  Future<void> askForNotificationPermissions() async {
    final response = await Permission.notification.request();
    notificationStatus = response;

    if (response.isGranted) {
      notificationsGranted = true;
    } else {
      notificationsGranted = false;
    }
    notifyListeners();
  }
}
