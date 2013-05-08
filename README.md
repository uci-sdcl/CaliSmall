CaliSmall
=========

Android version of Calico.

The development certificate to sign the app with can be found in CaliSmall/dev_keystore/dev.keystore.
Sign as user 'android', password is 'android'.

You can tell Eclipse to use that certificate by going to Eclipse's Preferences/Android/Build and typing the path to dev.keystore in the 'Custom Debug Keystore' field.

Unfortunately all your Android projects in the workspace will share this certificate, so if you want your other Android projects to use a different certificate you have to create different workspaces.
