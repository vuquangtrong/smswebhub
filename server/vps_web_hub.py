from http.server import BaseHTTPRequestHandler, HTTPServer
from datetime import datetime
import asyncio
import websockets
import threading
import urllib.parse

print("STABLE VERSION")

def getTime():
    """get timestamp for log"""
    return datetime.now().strftime("%d/%m/%Y %H:%M:%S")


# Web Socket is the relay between HTTP Web Server and Mobile App
class WebSocketThread(threading.Thread):
    """WebSocketThread will make websocket run in an a new thread"""

    # override self init
    def __init__(self):
        threading.Thread.__init__(self)
        self.USERS = set()

    # override run method
    def run(self):
        # must set a new loop for asyncio
        asyncio.set_event_loop(asyncio.new_event_loop())
        # setup a server, run at localhost
        print(getTime(), "Web Socket is running at port 8080")
        asyncio.get_event_loop().run_until_complete(websockets.serve(self.listen, '0.0.0.0', 8080))
        # keep thread running
        asyncio.get_event_loop().run_forever()

    # listener
    async def listen(self, websocket, path):
        """listener is called each time new client is connected
        websockets already ensures that a new thread is run for each client"""

        # register new client
        self.USERS.add(websocket)

        # this loop to get massage from client
        while True:
            try:
                msg = await websocket.recv()
                if msg is None:
                    break
                # await self.handle_message(websocket, msg)
                print(getTime(), "received: ", websocket, msg)

            except websockets.exceptions.ConnectionClosed:
                print(getTime(), "close: ", websocket)
                break

        self.USERS.remove(websocket)

    # message handler
    @staticmethod
    async def handle_message(client, data):
        print(getTime(), "received: ", client, data)

    # action
    async def forward_request(self, data):
        if self.USERS:  # broadcast data
            await asyncio.wait([user.send(data) for user in self.USERS])

    # expose action
    def do_forward_request(self, data):
        """this method is exposed to outside, not an async co-routine"""
        # use asyncio to run action
        # must call self.action(), not use self.action, because it must be a async co-routine
        asyncio.get_event_loop().run_until_complete(self.forward_request(data))


# Create a thread of Web Socket
threadWebSocket = WebSocketThread()
# start WebSocketThread
threadWebSocket.start()


# HTTP Web Server to listen to user GET Requests
# then extract the command and send to mobile via web socket
class SimpleHTTPRequestHandler(BaseHTTPRequestHandler):

    # handle GET request only
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(str.encode(getTime()))
        if "?" in self.path:
            # forward to web socket's clients
            threadWebSocket.do_forward_request(self.path)

    # handler POST request
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        body = self.rfile.read(content_length)
        print(getTime(), 'received: \n' + urllib.parse.unquote_plus(body.decode("utf-8")))
        self.send_response(200)
        self.end_headers()
        self.wfile.write(str.encode("Hell yeah!"))


# Create instance of Web Server
print(getTime(), "Web Server is running at port 8000")
httpd = HTTPServer(('0.0.0.0', 8000), SimpleHTTPRequestHandler)
# start HTTPServer
httpd.serve_forever()
