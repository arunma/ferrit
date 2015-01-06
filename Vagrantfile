Vagrant.configure(2) do |config|
  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "allenai/base"
  config.vm.provision :shell, path: "./provision.sh"

  # Increase RAM for VM
  config.vm.provider "virtualbox" do |v|
    v.memory = 4096
  end
end
