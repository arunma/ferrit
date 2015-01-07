Vagrant.configure(2) do |config|
  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "allenai/base"
  config.vm.provision :shell, path: "./provision.sh"

  # Forward our VM crawler port
  config.vm.network "forwarded_port", guest: 8181, host: 8181, auto_correct: true

  # Forward test-site AI2
  config.vm.network "forwarded_port", guest: 8080, host: 8080, auto_correct: true

  # Forward test-site UW Turing Center
  config.vm.network "forwarded_port", guest: 8081, host: 8081, auto_correct: true

  # Increase RAM for VM
  config.vm.provider "virtualbox" do |v|
    v.memory = 4096
  end
end
