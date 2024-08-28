import time

import codecraft

codecraft.CCClient("ws://127.0.0.1:6767")

codecraft.send_chat(f"Hello world!")
