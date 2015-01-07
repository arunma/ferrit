### Script for provisioning a Vagrant box
### By default we have sudo privileges, so only use 'sudo'
### if you need to specify a user (such as 'sudo -u ubuntu ... ')
set -v
set -e

# Add cassandra 
echo "deb http://debian.datastax.com/community stable main" | sudo tee -a /etc/apt/sources.list.d/cassandra.sources.list
curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
apt-get update
apt-get install -y dsc21
apt-get install -y cassandra-tools
cqlsh -f /vagrant/src/main/resources/cassandra-schema.sql

# Extract the test websites
mkdir /test-sites
tar xzf /vagrant/test-sites/allenai.org.tgz -C /test-sites/
tar xzf /vagrant/test-sites/turing.tgz -C /test-sites/

# Add nginx
apt-get install -y nginx
rm /etc/nginx/nginx.conf
ln -s /vagrant/conf/nginx.conf /etc/nginx/nginx.conf
nginx -s reload

echo "provisioned!"
