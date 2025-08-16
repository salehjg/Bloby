# Bloby App
This is the codebase for Bloby, a simple Python CLI tool to send or receive files over WIFI to or from your Android device. The idea is to treat a file as a blob, send it to the device. On the device, you can read this blob (as PDF for example), add your notes, annotate, and save it (ofc, in the app's private storage). Finally, whenever you want, you can send your blobs back to your linux machine by the app and the CLI utility.
Cool, eh?

## How?
The App creates a server and listens to it on activity start. The CLI script creates a socket and connects to it, sending the payload (for now). The CLI script could be found under `python/` directory.
