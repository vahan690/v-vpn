import UIKit
import Flutter
import Libcore

@main
@objc class AppDelegate: FlutterAppDelegate {
    
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        print("[AppDelegate] Starting application...")
        setupFileManager()
        print("[AppDelegate] File manager setup complete")
        registerHandlers()
        print("[AppDelegate] Handlers registered")
        GeneratedPluginRegistrant.register(with: self)
        print("[AppDelegate] Plugins registered, calling super...")
        let result = super.application(application, didFinishLaunchingWithOptions: launchOptions)
        print("[AppDelegate] Application launch complete")
        return result
    }

    func setupFileManager() {
        print("[AppDelegate] Setting up file manager...")
        print("[AppDelegate] Creating working directory at: \(FilePath.workingDirectory.path)")
        try? FileManager.default.createDirectory(at: FilePath.workingDirectory, withIntermediateDirectories: true)
        print("[AppDelegate] Changing current directory to: \(FilePath.sharedDirectory.path)")
        FileManager.default.changeCurrentDirectoryPath(FilePath.sharedDirectory.path)
        print("[AppDelegate] File manager setup done")
    }
    
    func registerHandlers() {
        MethodHandler.register(with: self.registrar(forPlugin: MethodHandler.name)!)
        PlatformMethodHandler.register(with: self.registrar(forPlugin: PlatformMethodHandler.name)!)
        FileMethodHandler.register(with: self.registrar(forPlugin: FileMethodHandler.name)!)
        StatusEventHandler.register(with: self.registrar(forPlugin: StatusEventHandler.name)!)
        AlertsEventHandler.register(with: self.registrar(forPlugin: AlertsEventHandler.name)!)
        LogsEventHandler.register(with: self.registrar(forPlugin: LogsEventHandler.name)!)
        GroupsEventHandler.register(with: self.registrar(forPlugin: GroupsEventHandler.name)!)
        ActiveGroupsEventHandler.register(with: self.registrar(forPlugin: ActiveGroupsEventHandler.name)!)
        StatsEventHandler.register(with: self.registrar(forPlugin: StatsEventHandler.name)!)
    }
}

