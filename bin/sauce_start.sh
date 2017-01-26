cd target
rm sauce_tunnel_ready 2> /dev/null
rm sauce_connect_pid 2> /dev/null

sc-dist/bin/sc \
  -u $SAUCE_USERNAME \
  -k $SAUCE_ACCESS_KEY \
  -i $TRAVIS_JOB_NUMBER \
  -f sauce_tunnel_ready \
  --vm-version dev-varnish &

PID=$!
echo -n $PID > sauce_connect_pid

# Wait tunnel creation
while [ ! -f sauce_tunnel_ready ]; do sleep 1; done

cd ..
