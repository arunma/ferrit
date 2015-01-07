# Test Environment

You can run Ferrit in a test environment with locally cached websites available for crawling. This is achieved using [vagrant](http://vagrantup.com).

## Vagrant Environment Setup

1. Install [VirtualBox](https://www.virtualbox.org/wiki/Downloads)

1. Install [Vagrant](https://www.vagrantup.com/downloads.html)

1. Fork this repository to your own user account

1. Install the [AWS CLI tools](http://aws.amazon.com/cli/)

1. Install the Allena AI base Vagrant box on your machine

```shell
$ aws s3 cp s3://ai2-environments/allenai-base.box allenai-base.box
$ vagrant box add allenai/base allenai-base.box
```

1. Run `vagrant up` from the root of your local forked repository

1. Run `vagrant ssh` to log into your dev VM

1. Start nginx (for some reason you have to start it each time the VM is rebooted - even though it is configured to boot on start).

```shell
$ sudo service nginx start
```

1. Start Ferrit

```shell
$ cd /vagrant/
$ sbt
> reStart
```

You should not be able to open your web browser to http://localhost:8181/crawlers and see an empty JSON array `[]` as a response.

We recommend using [Postman](http://www.getpostman.com/) for working with the Ferrit REST API.
