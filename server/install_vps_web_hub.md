# Install VPS Web Hub

1. Log in as root

2. Install python
    
        yum install python36

3. Upload `vps_web_hub.py` to your folder, for example `/root/smswebhub/`

4. Change attribute to executable
    
        chmod +x /root/smswebhub/vps_web_hub.py

5. Run web server
    
        nohup /usr/bin/python3 /root/smswebhub/vps_web_hub.py &

**Done. Server is running in background**  
**Read [Create a service](#create-a-service) section to do more**

6. Check open ports
    
        netstat -lntup

    You should see opened ports as below

        Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
        tcp        0      0 0.0.0.0:8000            0.0.0.0:*               LISTEN      1818/python3        

7. From internet, enter the vps server address with its port
    
        <vps_ip_address>:8000/<command>?[param=value]

    It should response with received timestamp

        04/12/2019 13:47:18

### Commands

Optinal param to select which SIM will be used to send SMS: _from_=_sim1_|_sim2_  
Only if _from_=_sim2_, SIM slot 2 will be used. Otherwise, SIM slot 1 or default SIM will be used.  
Default SIM might be your SIM slot 2 if the first slot is empty.

**SMS**

Send a SMS message to _number_ __X__, with _content_ __Y__, authorized by _your app token_ __Z__

    <vps_ip_address>:8000/sms?number=X&content=Y&token=Z[&from=sim1|sim2]

**USSD**

Query an _USSD code_ __X__ and forward response message to other host, authorized by _your app token_ __Z__  
Command __X__ will be added __*__ and __#__ to make completed USSD code.  
For exampleL _cmd_=_101_, USSD code will be __*101#__

    <vps_ip_address>:8000/ussd?cmd=X&token=Z[&from=sim1|sim2]

**CALL**

Make a call to _number_ __X__ and hangup after __Y__ second, authorized by _your app token_ __Z__  
If no _time_ is given, default is 5s

    <vps_ip_address>:8000/call?number=X&time=Y&token=Z[&from=sim1|sim2] 

To end call, simply use _end_=_y_

    <vps_ip_address>:8000/call?end=y&token=Z

### Create a service
Need to run webser as a service, auto start when VPS reboots?
Folow below steps:

1. Create new service description file
    
        nano /lib/systemd/system/smswebhub.service

2. Add service description into file

        [Unit]
        Description=SmsWebHub
        Requires=network.target
        After=network.target
        Conflicts=getty@tty1.service

        [Service]
        Type=simple
        ExecStart=/usr/bin/python3 /root/smswebhub/vps_web_hub.py
        StandardInput=tty-force
        Restart=always

        [Install]
        WantedBy=multi-user.target

3. Save file by press <kbd>Ctrl</kbd>+<kbd>X</kbd>, <kbd>Y</kbd>
4. Reload system service and start our service

        systemctl daemon-reload
        systemctl enable smswebhub.service
        systemctl start smswebhub.service

5. Check service status at anytime
    
        systemctl status smswebhub.service

    Example running service
    
        ● smswebhub.service - SmsWebHub
           Loaded: loaded (/usr/lib/systemd/system/smswebhub.service; enabled; vendor preset: disabled)
           Active: active (running) since Thu 2019-12-05 12:45:20 +07; 5s ago
         Main PID: 322 (python3)
           CGroup: /system.slice/smswebhub.service
                   └─322 /usr/bin/python3 /root/smswebhub/vps_web_hub.py

        Dec 05 12:45:20 vps systemd[1]: Started SmsWebHub.
        Dec 05 12:45:20 vps systemd[1]: Starting SmsWebHub...

