import os
import sys
import json
import urllib.request
import ssl

REPO = "Hisrogram/Drivekip"

def get_token():
    # 1. From CLI argument
    for arg in sys.argv[1:]:
        if arg.startswith("ghp_") or arg.startswith("github_pat_"):
            return arg.strip()
    # 2. From environment variable
    env_token = os.environ.get("GITHUB_TOKEN")
    if env_token:
        return env_token.strip()
    # 3. From local file
    token_file = os.path.join(os.path.dirname(__file__), "github_token.txt")
    if os.path.exists(token_file):
        with open(token_file, "r", encoding="utf-8") as f:
            t = f.read().strip()
            if t:
                return t
    return None

def publish(tag_name="v1.4.0", apk_path=None, notes="DriveKip 최신 업데이트"):
    token = get_token()
    if not token:
        print("=" * 60)
        print(" [!] GitHub Personal Access Token(PAT)이 필요합니다.")
        print(" 1) github_token.txt 파일에 토큰을 저장하거나")
        print(" 2) $env:GITHUB_TOKEN='ghp_...' 환경변수를 설정하거나")
        print(" 3) python publish_release.py ghp_... 로 실행해 주세요.")
        print("=" * 60)
        return False

    if not apk_path:
        apk_path = os.path.join(os.path.dirname(__file__), f"DriveKip-{tag_name}.apk")
    if not os.path.exists(apk_path):
        apk_path = os.path.join(os.path.dirname(__file__), "DriveKip-v1.4.0.apk")
    if not os.path.exists(apk_path):
        print(f"[!] APK 파일을 찾을 수 없습니다: {apk_path}")
        return False

    apk_size_mb = round(os.path.getsize(apk_path) / (1024 * 1024), 2)
    apk_name = os.path.basename(apk_path)
    print(f"[*] 대상 저장소: {REPO}")
    print(f"[*] 태그: {tag_name}")
    print(f"[*] 파일: {apk_name} ({apk_size_mb} MB)")

    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "DriveKip-Release-Tool",
        "X-GitHub-Api-Version": "2022-11-28"
    }

    # 1. Check or Create Release
    create_url = f"https://api.github.com/repos/{REPO}/releases"
    payload = json.dumps({
        "tag_name": tag_name,
        "name": f"DriveKip {tag_name}",
        "body": notes,
        "draft": False,
        "prerelease": False
    }).encode("utf-8")

    req = urllib.request.Request(create_url, data=payload, headers=headers, method="POST")
    release_data = None
    try:
        with urllib.request.urlopen(req, context=ctx) as res:
            release_data = json.loads(res.read().decode("utf-8"))
            print(f"[+] 새 릴리즈 생성 성공! ID: {release_data['id']}")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        if "already_exists" in err_msg or e.code == 422:
            print(f"[*] 태그 {tag_name}가 이미 존재합니다. 기존 릴리즈를 검색합니다...")
            req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{tag_name}", headers=headers)
            try:
                with urllib.request.urlopen(req_get, context=ctx) as res_get:
                    release_data = json.loads(res_get.read().decode("utf-8"))
                    print(f"[*] 기존 릴리즈 확인: ID {release_data['id']}")
            except Exception as e_get:
                print(f"[!] 기존 릴리즈 조회 실패: {e_get}")
                return False
        else:
            print(f"[!] 릴리즈 생성 오류 (HTTP {e.code}): {err_msg}")
            return False

    # Check if asset already exists in this release and delete old one
    if "assets" in release_data and release_data["assets"]:
        for asset in release_data["assets"]:
            if asset.get("name") == apk_name:
                print(f"[*] 이전 {apk_name} 파일 삭제 중 (Asset ID: {asset['id']})...")
                del_req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/assets/{asset['id']}", headers=headers, method="DELETE")
                try:
                    with urllib.request.urlopen(del_req, context=ctx) as del_res:
                        pass
                except Exception as del_err:
                    print(f"[!] 기존 파일 삭제 경고: {del_err}")

    # 2. Upload APK binary to Release
    upload_url_raw = release_data.get("upload_url", "")
    upload_base = upload_url_raw.split("{")[0]
    upload_url = f"{upload_base}?name={apk_name}"

    print(f"[*] GitHub Release에 APK 파일 업로드 중... ({apk_size_mb} MB)")
    upload_headers = headers.copy()
    upload_headers["Content-Type"] = "application/vnd.android.package-archive"

    with open(apk_path, "rb") as f:
        apk_data = f.read()

    req_upload = urllib.request.Request(upload_url, data=apk_data, headers=upload_headers, method="POST")
    try:
        with urllib.request.urlopen(req_upload, context=ctx) as u_res:
            u_info = json.loads(u_res.read().decode("utf-8"))
            download_url = u_info.get("browser_download_url")
            print("=" * 60)
            print(" [SUCCESS] GitHub Releases 업로드 성공!")
            print(f" [URL] 최신 릴리즈 다운로드 URL: {download_url}")
            print(" [INFO] 스마트폰 DriveKip 앱에서 '업데이트 확인'을 누르면 즉시 다운로드됩니다!")
            print("=" * 60)
            return True
    except urllib.error.HTTPError as e_up:
        print(f"[!] APK 파일 업로드 실패 (HTTP {e_up.code}): {e_up.read().decode('utf-8')}")
        return False

if __name__ == "__main__":
    tag = "v1.4.0"
    for arg in sys.argv[1:]:
        if arg.startswith("v") and len(arg) > 2:
            tag = arg
    publish(tag_name=tag)
