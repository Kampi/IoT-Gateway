# /var/log is a tmpfs (RAM): run logrotate hourly instead of daily, so a log
# file can't grow for a whole day. The rules themselves come with the services
# (/etc/logrotate.d/<service>, e.g. wmbusmeters).
LOGROTATE_SYSTEMD_TIMER_BASIS = "hourly"
LOGROTATE_SYSTEMD_TIMER_ACCURACY = "5m"
