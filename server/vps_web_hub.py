from io import BytesIO
from http.server import BaseHTTPRequestHandler, HTTPServer
import asyncio
import websockets
import threading
import urllib.parse

# Web Socket is the relay between HTTP Web Server and Mobile App
class WebSocketThread (threading.Thread):
    '''WebSocketThread will make websocket run in an a new thread'''

    # override self init
    def __init__(self, name):
        threading.Thread.__init__(self)
        self.name=name
        self.USERS = set()

    # override run method
    def run(self):
        # must set a new loop for asyncio
        asyncio.set_event_loop(asyncio.new_event_loop())
        # setup a server, run at localhost
        print("Web Socket is running at port 8080")
        asyncio.get_event_loop().run_until_complete(websockets.serve(self.listen, '0.0.0.0', 8080))
        # keep thread running
        asyncio.get_event_loop().run_forever()

    # listener
    async def listen(self, websocket, path):
        '''listener is called each time new client is connected
        websockets already ensures that a new thread is run for each client'''
        print("listen: ", websocket)

        # register new client
        self.USERS.add(websocket)

        # this loop to get massage from client
        while True:
            try:
                msg = await websocket.recv()
                if msg is None:
                    break
                await self.handle_message(websocket, msg)

            except websockets.exceptions.ConnectionClosed:
                print("close: ", websocket)
                break

        self.USERS.remove(websocket)

    # message handler
    async def handle_message(self, client, data):
        print("Received: ", client, data)

    # action
    async def forward_request(self, data):
        if self.USERS: # broadcast data
            await asyncio.wait([user.send(data) for user in self.USERS])

    # expose action
    def do_forward_request(self, data):
        '''this method is exposed to outside, not an async co-routine'''
        # use asyncio to run action
        # must call self.action(), not use self.action, because it must be a async co-routine
        asyncio.get_event_loop().run_until_complete(self.forward_request(data))

# Create a thread of Web Socket
threadWebSocket = WebSocketThread("webhub")

# HTTP Web Server to listen to user GET Requests
# then extract the command and send to mobile via web socket
class SimpleHTTPRequestHandler(BaseHTTPRequestHandler):

    # handle GET request only
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(str.encode(self.path))
        if "?" in self.path:
            # forward to web socket's clients
            threadWebSocket.do_forward_request(self.path)

    # handler POST request
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        body = self.rfile.read(content_length)
        print('Received: \n' + urllib.parse.unquote_plus(body.decode("utf-8")))
        self.send_response(200)
        self.end_headers()
        self.wfile.write(str.encode("Hell yeah!"))

# Create instance of Web Server
print("Web Server is runing at port 8000")
httpd = HTTPServer(('0.0.0.0', 8000), SimpleHTTPRequestHandler)

# start WebSocketThread
#threadWebSocket.start()

# start HTTPServer
#httpd.serve_forever()
