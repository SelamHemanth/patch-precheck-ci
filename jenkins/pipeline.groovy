//-----------------------------------------------------------------------------
//Groovy Script for run the jenkins pipeline.
//Author: Priyanka Mani
//Email: DMani.Priyanka@amd.com
//-----------------------------------------------------------------------------

def distros
def node_name = ""

// ===================== MAIN PIPELINE ============================

pipeline {
    agent { label node_name }

    parameters {
        choice(
            name: 'Distro',
            choices: ['none', 'anolis', 'openeuler'],
            description: 'Select the distro you want to test'
        )
        
        string(
            name: 'PATCH_DIR',
            defaultValue: 'none',
            trim: true,
            description: 'Linux source code path'
        )
        
        string(
            name: 'SIGNED_OFF_NAME',
            defaultValue: 'mohanasv',
            trim: true,
            description: 'Signed-off-by name'
        )
		
		string(
            name: 'SIGNED_OFF_EMAIL',
            defaultValue: 'mohanasv@amd.com',
            trim: true,
            description: 'Signed-off-by email'
        )
		
		string(
            name: 'BUGZILLA_ID',
            defaultValue: 'none',
            trim: true,
            description: 'Anolis Bugzilla ID'
        )
		
		string(
            name: 'NO_OF_PATCHES',
            defaultValue: 'none',
            trim: true,
            description: 'Number of patches to apply'
        )
        
        string(
            name: 'BUILD_THREADS',
            defaultValue: 'none',
            trim: true,
            description: 'Enter the number of CPUs'
        )
		
		choice(
            name: 'Anolis_Selected_tests',
            choices: ['none', 'check_Kconfig', 'build_allyes_config', 'build_allno_config', 'build_anolis_defconfig', 'build_anolis_debug', 'anck_rpm_build', 'check_kapi', 'boot_kernel_rpm', 'all'],
            description: 'If your selected distro is anolis, select the test cases'
        )
		
		choice(
			name: 'Patch_category',
			choices: ['none', 'feature', 'bugfix', 'performance', 'security'],
			description: 'If your selected distro is euler, Select one patch category'
		)
		
		choice(
			name: 'Euler_Selected_tests',
			choices: ['none', 'check_dependency', 'build_allmod', 'check_patch', 'check_format', 'rpm_build', 'boot_kernel', 'all'],
			description: 'If your selected distro is euler, Select the test cases'
		)
    }

    stages {

        stage('Initialization') {
            steps {
                script {
                    workspace_path = "${env.WORKSPACE}"
                    tool_path = "${env.WORKSPACE}/patch-precheck-ci"
                    build_path = "${env.WORKSPACE}/${env.BUILD_NUMBER}"
                    
                    echo "=========== DISTRO INITIALIZATION START ==========="                    
                    echo "${env.WORKSPACE}"
                    distros = detect_system_distro()
					run_distro_specific_operations(distros.system)
                    validate_distro_selection(distros)
                    

                    echo "=========== DISTRO INITIALIZATION COMPLETE =========="
                }
            }
        }
        
        stage('Configuration')
		{
			steps{
				script{
					echo "=========== CONFIGURATION START ============="
					echo "${distros.system}"
					if(distros.system == 'anolis')
					{
						anolis_general_configuration()
					}
					else if(distros.system == 'openeuler')
					{
						euler_general_configuration()
					}
					
					echo "=========== CONFIGURATION COMPLETE ============="
				}
			}
		}
		
		stage('Build')
		{
			steps{
				script{
					echo "============ BUILD START ============="
					sh """
						cd "${env.WORKSPACE}/patch-precheck-ci"
						make build
					"""
					
					echo "=========== BUILD COMPLETE ============="
				}
			}
		}
		
		stage('Test')
		{
			steps{
				script{
					echo "============ TESTING START ============="
					if(distros.system == 'anolis')
					{
						anolis_test_configuration()
					}
					if(distros.system == 'openeuler')
					{
						euler_test_configuration()
					}
					
					echo "============ TEST COMPLETE ==============="
				}
			}
		}
    }
}

// ===================== FUNCTION DEFINITIONS ======================
def detect_system_distro() {

    def system = sh(
        script: "awk -F= '/^ID=/{print \$2}' /etc/os-release | tr -d '\"'",
        returnStdout: true
    ).trim().toLowerCase()

    def user = params.Distro.toLowerCase()

    echo "Detected system distro: ${system}"
    echo "User selected: ${user}"

    return [system: system, user: user]
}

def validate_distro_selection(d) {

    if (d.user == 'none') {
        echo "No distro selected → Skipping validation."
        return
    }

    if (d.user != d.system) {
        echo "❌ MISMATCH — User selected ${d.user}, but system is ${d.system}"
        error("Stopping pipeline due to distro mismatch!")
    }

    echo "✔ MATCH — User selected distro matches system distro."
	echo "✔ MATCH — User selected distro matches system distro."

    sh """
        pwd

        if [ -d "${env.WORKSPACE}/patch-precheck-ci" ]; then
            echo "Directory patch-precheck-ci already exists - skipping clone."
        else
            echo "Directory not found - cloning repository..."
            cd "${env.WORKSPACE}"
            git clone https://github.com/SelamHemanth/patch-precheck-ci.git
        fi
    """
	
	if (d.system == 'anolis') {

		sh """
			echo "Detected distro: anolis"
			echo "${env.WORKSPACE}"
			cd "${env.WORKSPACE}/patch-precheck-ci"

			cat > .distro_config <<EOF
DISTRO=anolis
DISTRO_DIR=anolis
EOF

        echo ".distro_config created at:"
        pwd
        echo "File content:"
        cat .distro_config
    """
}
else if(d.system == 'openeuler'){
	sh """
			echo "Detected distro: euler"
			echo "${env.WORKSPACE}"
			cd "${env.WORKSPACE}/patch-precheck-ci"

			cat > .distro_config <<EOF
DISTRO=euler
DISTRO_DIR=euler
EOF

        echo ".distro_config created at:"
        pwd
        echo "File content:"
        cat .distro_config
    """
}


	
}

def run_distro_specific_operations(system_distro) {

    echo "Running distro-specific operations..."
    
    def packageManager = ''
    def installCommands = []
    
    switch(system_distro) {

        case 'anolis':
            echo "→ Detected OpenAnolis"
            packageManager = 'yum'
                    installCommands = [
                        "install -y git make automake python3-rpm bc flex bison rpm lz4 libunwind-devel python3-devel slang-devel numactl-devel libbabeltrace-devel capstone-devel libpfm-devel libtraceevent-devel",
                        "yum install -y audit-libs-devel binutils-devel libbpf-devel libcap-ng-devel libnl3-devel newt-devel pciutils-devel xmlto yum-utils",
                        "yum install -y sshpass"
                        ]
            echo "anolis package initialization completed............."
            
            break

        case 'openeuler':
        case 'euler':
            echo "→ Detected OpenEuler"
            packageManager = 'yum'
                    installCommands = [
                        "sudo yum install -y python3",
                        "sudo yum install -y sshpass"
                        ]
			echo "euler package initialization completed............."
            break

        default:
            echo "⚠ Unknown distro: ${system_distro}"
    }
}

def anolis_general_configuration(){
	sh """
		cd "${env.WORKSPACE}/patch-precheck-ci/anolis"
		cat > .configure <<'EOF'
# General Configuration
LINUX_SRC_PATH="${params.PATCH_DIR}"
SIGNER_NAME="${params.SIGNED_OFF_NAME}"
SIGNER_EMAIL="${params.SIGNED_OFF_EMAIL}"
ANBZ_ID="${params.BUGZILLA_ID.toInteger()}"
NUM_PATCHES="${params.NO_OF_PATCHES.toInteger()}"

# Build Configuration
BUILD_THREADS="${params.BUILD_THREADS.toInteger()}"

# Test Configuration	
RUN_TESTS="yes"
TEST_CHECK_KCONFIG="yes"
TEST_BUILD_ALLYES="yes"
TEST_BUILD_ALLNO="yes"
TEST_BUILD_DEFCONFIG="yes"
TEST_BUILD_DEBUG="yes"
TEST_RPM_BUILD="yes"
TEST_CHECK_KAPI="yes"
TEST_BOOT_KERNEL="yes"

# Host Configuration
HOST_USER_PWD='Amd\$1234!'

# VM Configuration
VM_IP="192.168.122.196"
VM_ROOT_PWD='Dma\$1234!'
EOF
	"""
}

def euler_general_configuration(){
	sh """
		cd "${env.WORKSPACE}/patch-precheck-ci/euler"
		cat > .configure <<'EOF'
# General Configuration
LINUX_SRC_PATH="${params.PATCH_DIR}"
SIGNER_NAME="${params.SIGNED_OFF_NAME}"
SIGNER_EMAIL="${params.SIGNED_OFF_EMAIL}"
BUGZILLA_ID="${params.BUGZILLA_ID}"
PATCH_CATEGORY="${params.PATCH_CATEGORY}"
NUM_PATCHES="${params.NO_OF_PATCHES.toInteger()}"

# Build Configuration
BUILD_THREADS="${params.BUILD_THREADS.toInteger()}"

# Test Configuration	
RUN_TESTS="yes"
TEST_CHECK_DEPENDENCY="yes"
TEST_BUILD_ALLMOD="yes"
TEST_CHECK_PATCH="yes"
TEST_CHECK_FORMAT="yes"
TEST_RPM_BUILD="yes"	
TEST_BOOT_KERNEL="yes"

# Host Configuration
HOST_USER_PWD='Amd\$1234!'

# VM Configuration
VM_IP="192.168.122.196"
VM_ROOT_PWD='Dma\$1234!'

# Repository Configuration
TORVALDS_REPO="${env.WORKSPACE}/patch-precheck-ci/.torvalds-linux"
EOF
	"""
}

def anolis_test_configuration() {
    def cmd = ""

    if (params.Anolis_Selected_tests == "all") {
        cmd = "make test"
    } else if (params.Anolis_Selected_tests == "check_Kconfig") {
        cmd = "make anolis-test=check_kconfig"
    } else if (params.Anolis_Selected_tests == "build_allyes_config") {
        cmd = "make anolis-test=build_allyes_config"
    } else if (params.Anolis_Selected_tests == "build_allno_config") {
        cmd = "make anolis-test=build_allno_config"
    } else if (params.Anolis_Selected_tests == "build_anolis_defconfig") {
        cmd = "make anolis-test=build_anolis_defconfig"
    } else if (params.Anolis_Selected_tests == "build_anolis_debug") {
        cmd = "make anolis-test=build_anolis_debug"
    } else if (params.Anolis_Selected_tests == "anck_rpm_build") {
        cmd = "make anolis-test=anck_rpm_build"
    } else if (params.Anolis_Selected_tests == "check_kapi") {
        cmd = "make anolis-test=check_kapi"
    } else if (params.Anolis_Selected_tests == "boot_kernel_rpm") {
        cmd = "make anolis-test=boot_kernel_rpm"
    } else if (params.Anolis_Selected_tests == "none") {
        cmd = "echo 'no test is selected to run....'"
    }

    sh """
        cd "${env.WORKSPACE}/patch-precheck-ci"
        ${cmd}
    """
}

def euler_test_configuration(){
	def cmd = ""

    if (params.Euler_Selected_tests == "all") {
        cmd = "make test"
    } else if (params.Euler_Selected_tests == "check_dependency") {
        cmd = "make euler-test=check_dependency"
    } else if (params.Euler_Selected_tests == "build_allmod") {
        cmd = "make euler-test=build_allmod"
    } else if (params.Euler_Selected_tests == "check_patch") {
        cmd = "make euler-test=check_patch"
    } else if (params.Euler_Selected_tests == "check_format") {
        cmd = "make euler-test=check_format"
    } else if (params.Euler_Selected_tests == "rpm_build") {
        cmd = "make euler-test=rpm_build"
    } else if (params.Euler_Selected_tests == "boot_kernel") {
        cmd = "make euler-test=boot_kernel"
    } else if (params.Euler_Selected_tests == "none") {
        cmd = "echo 'no test is selected to run....'"
    }

    sh """
        cd "${env.WORKSPACE}/patch-precheck-ci"
        ${cmd}
    """
}
