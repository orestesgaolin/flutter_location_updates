import 'package:flutter/material.dart';
import 'package:location/foreground_service.dart';
import 'package:location/permissions_service.dart';
import 'package:provider/provider.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  final permissionsService = PermissionsService()
    ..checkLocationPermissions()
    ..checkNotificationPermissions();

  final foregroundService = ForegroundService();

  runApp(
    MainApp(
      permissionsService: permissionsService,
      foregroundService: foregroundService,
    ),
  );
}

class MainApp extends StatelessWidget {
  const MainApp({
    super.key,
    required this.permissionsService,
    required this.foregroundService,
  });

  final PermissionsService permissionsService;
  final ForegroundService foregroundService;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<PermissionsService>.value(
      value: permissionsService,
      child: ChangeNotifierProvider<ForegroundService>.value(
        value: foregroundService,
        child: const MaterialApp(
          home: MainBody(),
        ),
      ),
    );
  }
}

class MainBody extends StatelessWidget {
  const MainBody({
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Column(
        children: [
          LocationsPermissionsWidget(),
          NotificationsPermissionsWidget(),
          ForegroundServiceButtons(),
          Expanded(
            child: LocationUpdates(),
          )
        ],
      ),
    );
  }
}

class LocationUpdates extends StatelessWidget {
  const LocationUpdates({super.key});

  @override
  Widget build(BuildContext context) {
    final service = context.watch<ForegroundService>();
    if (service.connected) {
      return const LocationsList();
    } else {
      return const Text('Service not connected');
    }
  }
}

class LocationsList extends StatefulWidget {
  const LocationsList({
    super.key,
  });

  @override
  State<LocationsList> createState() => _LocationsListState();
}

class _LocationsListState extends State<LocationsList> {
  late final Stream<String> stream;

  @override
  void initState() {
    super.initState();
    final service = context.read<ForegroundService>();
    stream = service.location();
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<String>(
        stream: stream,
        builder: (context, snapshot) {
          if (snapshot.hasData) {
            return Center(
              child: Text(snapshot.data ?? 'no data'),
            );
          } else {
            return const Center(
              child: Text('No data'),
            );
          }
        });
  }
}

class ForegroundServiceButtons extends StatelessWidget {
  const ForegroundServiceButtons({super.key});

  @override
  Widget build(BuildContext context) {
    final foregroundService = context.watch<ForegroundService>();
    if (foregroundService.connected) {
      return Padding(
        padding: const EdgeInsets.all(8.0),
        child: ElevatedButton(
          onPressed: () async {
            await foregroundService.stop();
          },
          child: const Text('Stop service'),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: ElevatedButton(
        onPressed: () async {
          await foregroundService.startOrConnect();
        },
        child: const Text('Start service'),
      ),
    );
  }
}

class LocationsPermissionsWidget extends StatelessWidget {
  const LocationsPermissionsWidget({
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final permissionsService = context.read<PermissionsService>();
    return SafeArea(
      minimum: const EdgeInsets.all(8.0),
      child: ListenableBuilder(
        listenable: permissionsService,
        builder: (context, _) {
          if (permissionsService.permissionsGranted) {
            return const Text('Permissions granted');
          } else {
            return ElevatedButton(
              onPressed: () async {
                await permissionsService.askForLocationPermissions();
              },
              child: const Text('Ask for permissions'),
            );
          }
        },
      ),
    );
  }
}

class NotificationsPermissionsWidget extends StatelessWidget {
  const NotificationsPermissionsWidget({super.key});

  @override
  Widget build(BuildContext context) {
    final permissionsService = context.read<PermissionsService>();
    return SafeArea(
      minimum: const EdgeInsets.all(8.0),
      child: ListenableBuilder(
        listenable: permissionsService,
        builder: (context, _) {
          if (permissionsService.notificationsGranted) {
            return const Text('Notifications granted');
          } else {
            return ElevatedButton(
              onPressed: () async {
                await permissionsService.askForNotificationPermissions();
              },
              child: const Text('Ask for notifications permissions'),
            );
          }
        },
      ),
    );
  }
}
