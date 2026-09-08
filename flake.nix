{
  description = "Split Voice Android development";

  inputs = {
    # see docs at https://flake.parts/
    flake-parts.url = "github:hercules-ci/flake-parts";
    nixpkgs.url = "github:nixos/nixpkgs/nixos-26.05";
  };

  outputs =
    inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      flake = { };
      systems = [ "x86_64-linux" ];
      perSystem =
        { pkgs, ... }:
        let
          androidPkgs = import inputs.nixpkgs {
            system = pkgs.stdenv.hostPlatform.system;
            config = {
              allowUnfree = true;
              android_sdk.accept_license = true;
            };
          };
          sdk = (androidPkgs.androidenv.composeAndroidPackages {
            platformVersions = [ "35" ];
            buildToolsVersions = [ "35.0.0" ];
            includeEmulator = false;
            includeSources = false;
            includeNDK = false;
          }).androidsdk;
        in
        {
          legacyPackages = pkgs;
          devShells.default = pkgs.mkShell {
            name = "split-voice";
            packages = [ pkgs.jdk17 pkgs.gradle sdk pkgs.shellcheck ];
            JAVA_HOME = "${pkgs.jdk17}";
            ANDROID_HOME = "${sdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${sdk}/libexec/android-sdk";
          };
        };
    };
}
