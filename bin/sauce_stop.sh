cd target

PID=`cat sauce_connect_pid`
kill $PID
wait $PID
rm sauce_connect_pid

cd ..
