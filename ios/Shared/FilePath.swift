//
//  FilePath.swift
//  SingBoxPacketTunnel
//
//  Created by GFWFighter on 7/25/1402 AP.
//

import Foundation

public enum FilePath {
    public static let packageName = {
        Bundle.main.infoDictionary?["BASE_BUNDLE_IDENTIFIER"] as? String ?? "unknown"
    }()
}

public extension FilePath {
    static let groupName = "group.\(packageName)"

    private static let defaultSharedDirectory: URL? = {
        let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: FilePath.groupName)
        if url == nil {
            print("[FilePath] ERROR: Failed to get container URL for App Group: \(FilePath.groupName)")
            print("[FilePath] packageName: \(packageName)")
        } else {
            print("[FilePath] Successfully got container URL: \(url!.path)")
        }
        return url
    }()

    static var sharedDirectory: URL {
        if let url = defaultSharedDirectory {
            return url
        }
        // Fallback to documents directory if App Group fails
        print("[FilePath] WARNING: Using fallback documents directory")
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    static let cacheDirectory = sharedDirectory
        .appendingPathComponent("Library", isDirectory: true)
        .appendingPathComponent("Caches", isDirectory: true)

    static let workingDirectory = cacheDirectory.appendingPathComponent("Working", isDirectory: true)
}

public extension URL {
    var fileName: String {
        var path = relativePath
        if let index = path.lastIndex(of: "/") {
            path = String(path[path.index(index, offsetBy: 1)...])
        }
        return path
    }
}
