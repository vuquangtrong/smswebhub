from vps_web_hub import *

# start WebSocketThread
threadWebSocket.start()

# start HTTPServer
httpd.serve_forever()
