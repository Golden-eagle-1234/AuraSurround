#!/usr/bin/env bash
set -e

# Color definitions
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly MAGENTA='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly WHITE='\033[1;37m'
readonly BOLD='\033[1m'
readonly DIM='\033[2m'
readonly NC='\033[0m' # No Color
readonly BG_BLACK='\033[40m'
readonly BG_BLUE='\033[44m'
readonly BG_GREEN='\033[42m'
readonly BG_RED='\033[41m'

# Unicode symbols
readonly CHECK_MARK="✓"
readonly CROSS_MARK="✗"
readonly ARROW="→"
readonly STAR="★"
readonly GEAR="⚙"
readonly FOLDER="📁"
readonly ROCKET="🚀"
readonly LOCK="🔒"
readonly GLOBE="🌐"
readonly SPARKLES="✨"

# Get terminal width safely without tput
get_terminal_width() {
    # Try tput first, fall back to stty, then default to 80
    if command -v tput &> /dev/null; then
        tput cols 2>/dev/null || echo 80
    elif command -v stty &> /dev/null; then
        stty size 2>/dev/null | cut -d' ' -f2 || echo 80
    else
        echo 80
    fi
}

# Terminal width for UI elements
TERM_WIDTH=$(get_terminal_width)

# Function to repeat characters (bash 3.0+ compatible)
repeat_char() {
    local char="$1"
    local count="$2"
    local result=""
    for ((i=0; i<count; i++)); do
        result+="$char"
    done
    echo "$result"
}

# Clear screen with animation
clear_screen() {
    clear 2>/dev/null || printf "\033c"
    echo -e "${BLUE}$(repeat_char '━' $TERM_WIDTH)${NC}"
    echo -e "${BOLD}${WHITE}${SPARKLES} GitHub Clean Termux Uploader Pro ${SPARKLES}${NC}"
    echo -e "${DIM}Advanced Repository Management Tool v2.0${NC}"
    echo -e "${BLUE}$(repeat_char '━' $TERM_WIDTH)${NC}"
    echo ""
}

# Simple fake progress bar (doesn't block)
fake_progress() {
    local steps=10
    for ((i=1; i<=steps; i++)); do
        local percent=$((i * 10))
        local filled=$((i * 5))
        local empty=$((50 - filled))
        echo -ne "${CYAN}["
        for ((j=0; j<filled; j++)); do echo -ne "█"; done
        for ((j=0; j<empty; j++)); do echo -ne "░"; done
        echo -ne "] ${percent}%${NC}\r"
        sleep 0.1
    done
    echo -e "${GREEN}[██████████████████████████████████████████████████] 100%${NC}"
}

# Status messages
success_msg() {
    echo -e "${GREEN}${CHECK_MARK}${NC} ${1}"
}

error_msg() {
    echo -e "${RED}${CROSS_MARK}${NC} ${1}"
}

info_msg() {
    echo -e "${BLUE}${ARROW}${NC} ${1}"
}

warning_msg() {
    echo -e "${YELLOW}${STAR}${NC} ${1}"
}

# Fancy header box
draw_box() {
    local title="$1"
    local width=60
    local padding=$(( (TERM_WIDTH - width) / 2 ))
    [ $padding -lt 0 ] && padding=0
    
    local horizontal_line=$(repeat_char '═' $((width-2)))
    
    printf "%${padding}s" ""
    echo -e "${BLUE}╔${horizontal_line}╗${NC}"
    printf "%${padding}s" ""
    printf "${BLUE}║${NC}${BOLD}%-$((width-2))s${NC}${BLUE}║${NC}\n" "$title"
    printf "%${padding}s" ""
    echo -e "${BLUE}╚${horizontal_line}╝${NC}"
}

# Input validation
validate_input() {
    local input="$1"
    local type="$2"
    
    case $type in
        "username")
            if [[ ! "$input" =~ ^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$ ]]; then
                error_msg "Invalid GitHub username format"
                return 1
            fi
            ;;
        "repo")
            if [[ ! "$input" =~ ^[a-zA-Z0-9._-]+$ ]]; then
                error_msg "Invalid repository name format"
                return 1
            fi
            ;;
        "token")
            if [[ ${#input} -lt 10 ]]; then
                error_msg "Token seems too short (minimum 10 characters)"
                return 1
            fi
            ;;
    esac
    return 0
}

# Enhanced input with validation
get_validated_input() {
    local prompt="$1"
    local var_name="$2"
    local input_type="$3"
    local is_secret="$4"
    local value=""
    
    while true; do
        if [ "$is_secret" = "true" ]; then
            read -s -p "$(echo -e "${CYAN}${LOCK} ${prompt}${NC}: ")" value
            echo ""
        else
            read -p "$(echo -e "${CYAN}${ARROW} ${prompt}${NC}: ")" value
        fi
        
        if [ -z "$value" ]; then
            error_msg "Input cannot be empty"
            continue
        fi
        
        if validate_input "$value" "$input_type"; then
            eval "$var_name='$value'"
            success_msg "Valid input received"
            break
        fi
    done
}

# Check dependencies
check_dependencies() {
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Checking system dependencies..."
    
    local missing_deps=()
    
    if ! command -v git &> /dev/null; then
        missing_deps+=("git")
    fi
    
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    fi
    
    if [ ${#missing_deps[@]} -gt 0 ]; then
        error_msg "Missing dependencies: ${missing_deps[*]}"
        read -p "$(echo -e "${YELLOW}Would you like to install them? (y/n)${NC}: ")" install_deps
        
        if [[ "$install_deps" =~ ^[Yy]$ ]]; then
            info_msg "Installing dependencies..."
            pkg install -y "${missing_deps[@]}" &> /dev/null
            fake_progress
            success_msg "Dependencies installed successfully"
        else
            error_msg "Cannot proceed without required dependencies"
            exit 1
        fi
    else
        success_msg "All dependencies satisfied"
    fi
}

# Test GitHub connectivity
test_connection() {
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Testing GitHub connectivity..."
    
    if command -v curl &> /dev/null; then
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" "https://api.github.com" 2>/dev/null)
        if [ "$http_code" = "200" ]; then
            success_msg "Connected to GitHub"
            return 0
        fi
    fi
    
    error_msg "Cannot reach GitHub. Check your internet connection"
    return 1
}

# Repository size check
check_repo_size() {
    local total_size="unknown"
    if command -v du &> /dev/null; then
        total_size=$(du -sh . 2>/dev/null | cut -f1)
        info_msg "Repository size: ${BOLD}${total_size}${NC}"
    fi
}

# Gitignore management
manage_gitignore() {
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Managing .gitignore file..."
    
    if [ ! -f ".gitignore" ]; then
        warning_msg "No .gitignore found"
        read -p "$(echo -e "${YELLOW}Would you like to create one? (y/n)${NC}: ")" create_gitignore
        
        if [[ "$create_gitignore" =~ ^[Yy]$ ]]; then
            cat > .gitignore << 'GITIGNORE_EOF'
# Termux specific
*.apk
*.dex
*.class
*.so
*.o

# Build outputs
bin/
obj/
build/
dist/

# IDE
.vscode/
.idea/
*.swp
*.swo
*~

# OS files
.DS_Store
Thumbs.db

# Logs
*.log
logs/
GITIGNORE_EOF
            success_msg "Created .gitignore with sensible defaults"
        fi
    else
        local line_count=$(wc -l < .gitignore 2>/dev/null || echo "unknown")
        success_msg "Existing .gitignore found (${line_count} lines)"
    fi
}

# Branch management
manage_branches() {
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Branch configuration..."
    
    local branches=""
    if [ -d ".git" ]; then
        branches=$(git branch 2>/dev/null)
        if [ -n "$branches" ]; then
            echo -e "${DIM}Existing branches:${NC}"
            echo "$branches"
        fi
    fi
    
    # Let user choose branch name
    read -p "$(echo -e "${CYAN}${ARROW} Enter branch name (default: main)${NC}: ")" branch_name
    branch_name=${branch_name:-main}
    
    echo -e "${DIM}Using branch: ${BOLD}${branch_name}${NC}"
}

# Simple file statistics
show_file_stats() {
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    draw_box "${FOLDER} File Statistics"
    
    local total_files=0
    local total_dirs=0
    
    if command -v find &> /dev/null; then
        total_files=$(find . -type f -not -path './.git/*' 2>/dev/null | wc -l)
        total_dirs=$(find . -type d -not -path './.git/*' 2>/dev/null | wc -l)
    fi
    
    echo -e "${WHITE}Total Files:${NC} ${BOLD}$total_files${NC}"
    echo -e "${WHITE}Total Directories:${NC} ${BOLD}$total_dirs${NC}"
    
    # Show some files
    echo -e "\n${YELLOW}${STAR} Recent files:${NC}"
    if command -v find &> /dev/null; then
        find . -type f -not -path './.git/*' -printf '%T@ %p\n' 2>/dev/null | \
            sort -rn | head -3 | cut -d' ' -f2- | \
            while read file; do
                echo -e "  ${DIM}${file#./}${NC}"
            done
    fi
}

# Main execution
main() {
    clear_screen
    
    # Check dependencies
    check_dependencies
    
    # Test connectivity
    test_connection || exit 1
    
    # Get credentials with validation
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    draw_box "${GLOBE} Repository Configuration"
    
    get_validated_input "GitHub Username" "USERNAME" "username" "false"
    get_validated_input "Repository Name" "REPO" "repo" "false"
    get_validated_input "Personal Access Token" "TOKEN" "token" "true"
    
    # Fix Android storage permissions
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Configuring Android file system permissions..."
    CURRENT_DIR=$(pwd)
    git config --global --add safe.directory "$CURRENT_DIR" 2>/dev/null || true
    success_msg "Permissions configured"
    
    # Initialize repository
    if [ ! -d ".git" ]; then
        echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
        info_msg "Initializing new repository..."
        git init
        fake_progress
        success_msg "Repository initialized"
    else
        success_msg "Existing repository detected"
    fi
    
    # Configure git
    info_msg "Configuring git credentials..."
    git config user.name "$USERNAME"
    git config user.email "${USERNAME}@users.noreply.github.com"
    success_msg "Git credentials configured"
    
    # Manage gitignore
    manage_gitignore
    
    # Show statistics
    show_file_stats
    
    # Branch management
    manage_branches
    
    # Check size
    check_repo_size
    
    # Reset remote
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Configuring remote repository..."
    git remote remove origin 2>/dev/null || true
    
    REMOTE_URL="https://${USERNAME}:${TOKEN}@github.com/${USERNAME}/${REPO}.git"
    git remote add origin "$REMOTE_URL"
    success_msg "Remote repository configured: ${DIM}https://github.com/${USERNAME}/${REPO}.git${NC}"
    
    # Stage files
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Staging files for commit..."
    git add .
    success_msg "Files staged"
    
    # Commit
    info_msg "Creating commit..."
    read -p "$(echo -e "${CYAN}${ARROW} Enter commit message (default: auto-generated)${NC}: ")" commit_msg
    commit_msg=${commit_msg:-"Upload from Termux - $(date '+%Y-%m-%d %H:%M:%S')"}
    
    if git commit -m "$commit_msg" 2>/dev/null; then
        success_msg "Commit created: ${DIM}${commit_msg}${NC}"
    else
        info_msg "No new changes to commit"
    fi
    
    # Push
    echo -e "\n${BLUE}$(repeat_char '─' $TERM_WIDTH)${NC}"
    info_msg "Preparing to push to GitHub..."
    git branch -M "$branch_name"
    
    echo -e "${YELLOW}${ROCKET} Uploading to GitHub...${NC}"
    
    if git push -u origin "$branch_name" 2>&1; then
        echo -e "\n${BG_GREEN}${WHITE}${BOLD} SUCCESS ${NC}"
        echo -e "${GREEN}$(repeat_char '─' $TERM_WIDTH)${NC}"
        echo -e "${GREEN}${SPARKLES} Repository successfully uploaded!${NC}"
        echo -e "${GREEN}${GLOBE} View at: https://github.com/${USERNAME}/${REPO}${NC}"
        echo -e "${GREEN}$(repeat_char '─' $TERM_WIDTH)${NC}\n"
    else
        echo -e "\n${BG_RED}${WHITE}${BOLD} ERROR ${NC}"
        error_msg "Failed to push to GitHub"
        echo -e "${YELLOW}Tips:${NC}"
        echo -e "  ${DIM}• Check your token permissions${NC}"
        echo -e "  ${DIM}• Verify repository name${NC}"
        echo -e "  ${DIM}• Ensure repository doesn't already exist${NC}"
        exit 1
    fi
}

# Trap for clean exit
trap 'echo -e "\n${RED}${CROSS_MARK} Operation cancelled${NC}"; exit 1' INT TERM

# Run main function
main