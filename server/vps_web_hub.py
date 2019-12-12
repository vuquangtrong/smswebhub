#!/usr/bin/python3

from datetime import datetime
import asyncio
import websockets
import http

CLIENTS = set()
MESSAGE = None


def get_time():
    """get timestamp for log"""
    return datetime.now().strftime("%d/%m/%Y %H:%M:%S")


def get_timestamp():
    """get timestamp for web request"""
    return datetime.now().strftime("%Y%m%d%H%M%S")


async def forward_request(data):
    if CLIENTS:  # asyncio.wait doesn't accept an empty list
        # print(get_time(), "forward: ", data)
        await asyncio.wait([client.send(data) for client in CLIENTS])


async def register(client):
    # print("connect: ", client)
    CLIENTS.add(client)


async def unregister(client):
    # print("close: ", client)
    CLIENTS.remove(client)


async def http_handler(path, request_headers):
    global MESSAGE
    # print(get_time(), "http_handler: ", path, request_headers)
    if "?" in path:
        MESSAGE = "" + path + "&ts=" + get_timestamp()
        return http.HTTPStatus.OK, [], str.encode(get_time())


async def ws_handler(client, path):
    global MESSAGE
    # print(get_time(), "ws_handler: ", client, path)
    await register(client)
    try:
        while True:
            # read
            try:
                await asyncio.wait_for(client.recv(), timeout=1)
            except websockets.exceptions.ConnectionClosed:
                break
            except:
                pass

            # write
            if MESSAGE is not None:
                await forward_request(MESSAGE)
                MESSAGE = None
    finally:
        await unregister(client)


print(get_time(), "Start WebSocket Server at :8000")
print(get_time(), "Version = 1.0")
wsServer = websockets.serve(
    ws_handler,
    "0.0.0.0",
    8000,
    process_request=http_handler
)

# start async tasks
asyncio.get_event_loop().run_until_complete(wsServer)
asyncio.get_event_loop().run_forever()
