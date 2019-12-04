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

6. Check open ports
	
		netstat -lntup

	You should see opened ports as below

		Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name    
		tcp        0      0 0.0.0.0:8000            0.0.0.0:*               LISTEN      1818/python3        
		tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN      1818/python3  

7. From internet, enter the vps server address with its port
	
		<vps_ip_address>:8000

	It should response with received timestamp

		04/12/2019~13:47:18

### Optinal
Need to run webser as a service, auto start when VPS reboots?
Folow below steps:

1. Create new service description file
	
		nano /lib/systemd/system/smswebhub.service

2. Add service description into file

		[Unit]
		Description=SmsWebHub
		After=multi-user.target
		Conflicts=getty@tty1.service

		[Service]
		Type=simple
		ExecStart=nohup /usr/bin/python3 /root/smswebhub/vps_web_hub.py &
		StandardInput=tty-force

		[Install]
		WantedBy=multi-user.target

3. Save file by press <kbd>Ctrl</kbd>+<kbd>X</kbd>, <kbd>Y</kbd>
4. Reload system service and start our service

		systemctl daemon-reload
		systemctl enable smswebhub.service
		systemctl start smswebhub.service

