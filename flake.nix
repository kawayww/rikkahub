{
  description = "RikkaHub Android development shell";

  inputs.nixpkgs.url = "tarball+https://github.com/NixOS/nixpkgs/archive/refs/heads/nixos-unstable.tar.gz";

  outputs = { nixpkgs, ... }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };

          androidSdk = pkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "37" ];
            buildToolsVersions = [ "37.0.0" ];
            includeEmulator = false;
            includeCmake = false;
            includeSystemImages = false;
          };
        in
        {
          default = pkgs.mkShell {
            packages = [
              pkgs.jdk17
              pkgs.nodejs_22
              pkgs.bun
              androidSdk.androidsdk
            ];

            JAVA_HOME = pkgs.jdk17.home;
            ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";

            shellHook = ''
              export PATH="$ANDROID_HOME/platform-tools:$PATH"
            '';
          };
        });
    };
}
