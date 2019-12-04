firewallD is not running

enable:

	systemctl enable firewalld
	systemctl start firewalld
	systemctl status firewalld

add rule:

	firewall-cmd --get-active-zones
	firewall-cmd --zone=public --add-port=8000/tcp --permanent
	firewall-cmd --zone=public --add-port=8080/tcp --permanent
	firewall-cmd --reload

or

	nano /etc/services
	service-name  port/protocol  [aliases ...]   [# comment]
	testport        55555/tcp   # Application Name

check

	iptables-save
	netstat -lntup

install python

	yum install python37


sftp upload python code
delete with shred -zvu

run in background

	add shebang #!/usr/bin/python3
	chmod +x run.py




Địa chỉ IP:	103.104.119.189
Port quản trị:	22
Tài khoản:	root
Mật khẩu:	Ql9nY8#mxDW]50


nano /lib/systemd/system/smswebhub.service

[Unit]
Description=SMS WEB HUB
After=multi-user.target
Conflicts=getty@tty1.service

[Service]
Type=simple
ExecStart=nohup /usr/bin/python3 /root/smswebhub/run.py &
StandardInput=tty-force

[Install]
WantedBy=multi-user.target

systemctl daemon-reload
systemctl enable smswebhub.service
systemctl start smswebhub.service
systemctl status smswebhub.service
systemctl stop smswebhub.service
systemctl restart smswebhub.service




python3 vps_web_hub.py >log.txt 2>&1 &
tail -F log.txt

