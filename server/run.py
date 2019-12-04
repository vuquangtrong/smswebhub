#!/usr/bin/python3

from vps_web_hub import *

threadWebSocket.start()
httpd.serve_forever()