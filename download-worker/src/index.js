const APK_CONTENT_TYPE = "application/vnd.android.package-archive";

const CODE41_KEY = "releases/arunomatic/v0.1.9-winning-share1/ARUNOMATIC_v0.1.9-winning-share1-code41.apk";
const CODE41_SHA = "942cdf43ae4d4e23d8385b955b0f0631c741ec43d04adb874ca459b4c3f42dba";

const CODE42_KEY = "releases/arunomatic/v0.1.9-winning-share2/ARUNOMATIC_v0.1.9-winning-share2-code42.apk";
const CODE42_SHA = "ce9a9b8eea91fe600b85f0f90c1e946eb1830a93e2b0361a5cf1f5cd25252e8b";

const CODE43_KEY = "releases/arunomatic/v0.1.9-winning-share3/ARUNOMATIC_v0.1.9-winning-share3-code43.apk";
const CODE43_SHA = "e51c37e68cddb2d607e79982e0ced7c7e1063f653fe3dd3476640bb46bc2b0de";
const CODE44_CANDIDATE_KEY = "candidates/arunomatic/code44/a50ba22/ARUNOMATIC-code44-candidate-a50ba22.apk";
const CODE44_CANDIDATE_SHA = "bd7a31e3e8b5caedb4cf845184991e777142fda0ca0ed9525530191d9f2139cc";
const CODE45_KEY = "releases/arunomatic/v0.2.1-control-status/ARUNOMATIC_v0.2.1-control-status-code45.apk";
const CODE45_SHA = "dec5804646e3ff7f02a53c0afb3a94022b38622c2844d1d2592c8e3e099db119";
const CODE46_KEY = "releases/arunomatic/v0.2.2-control-layout/ARUNOMATIC_v0.2.2-control-layout-code46.apk";
const CODE46_SHA = "e776f7d8275a493961c8ef90125cebd07ff046570a8d81d47b07271e5713d6ca";
const CODE47_KEY = "releases/arunomatic/v0.2.3-dock-position/ARUNOMATIC_v0.2.3-dock-position-code47.apk";
const CODE47_SHA = "e76892b29420e5f4b15b3f2fd3129112488c4cee4eb1f25a847a500b9be7dc52";
const DOWNLOADS = new Map([
  ["/apk/arunomatic-code47.apk", {key:CODE47_KEY,filename:"ARUNOMATIC_v0.2.3-dock-position-code47.apk",cacheControl:"no-store",expectedSha:CODE47_SHA}],
  ["/apk/arunomatic-code46.apk", {key:CODE46_KEY,filename:"ARUNOMATIC_v0.2.2-control-layout-code46.apk",cacheControl:"no-store",expectedSha:CODE46_SHA}],
  ["/apk/arunomatic-code45.apk", {key:CODE45_KEY,filename:"ARUNOMATIC_v0.2.1-control-status-code45.apk",cacheControl:"no-store",expectedSha:CODE45_SHA}],
  ["/apk/arunomatic-code44-candidate.apk", {key:CODE44_CANDIDATE_KEY,filename:"ARUNOMATIC-code44-candidate-a50ba22.apk",cacheControl:"no-store",expectedSha:CODE44_CANDIDATE_SHA}],
  ["/apk/arunomatic-code43.apk", {key:CODE43_KEY,filename:"ARUNOMATIC_v0.1.9-winning-share3-code43.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code42.apk", {key:CODE42_KEY,filename:"ARUNOMATIC_v0.1.9-winning-share2-code42.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code41.apk", {key:CODE41_KEY,filename:"ARUNOMATIC_v0.1.9-winning-share1-code41.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code39.apk", {key:"releases/arunomatic/v0.1.8-device-test1/ARUNOMATIC_v0.1.8-device-test1-code39.apk",filename:"ARUNOMATIC_v0.1.8-device-test1-code39.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code38.apk", {key:"releases/arunomatic/v0.1.7-air-display1/ARUNOMATIC_v0.1.7-air-display1-code38.apk",filename:"ARUNOMATIC_v0.1.7-air-display1-code38.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code37.apk", {key:"releases/arunomatic/v0.1.7-air-edit1/ARUNOMATIC_v0.1.7-air-edit1-code37.apk",filename:"ARUNOMATIC_v0.1.7-air-edit1-code37.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-code36.apk", {key:"releases/arunomatic/v0.1.7-devicecontrol2/ARUNOMATIC_v0.1.7-devicecontrol2-code36.apk",filename:"ARUNOMATIC_v0.1.7-devicecontrol2-code36.apk",cacheControl:"no-store"}],
  ["/apk/arunomatic-pageflag-diag1.apk", { key: "apk/arunomatic-pageflag-diag1.apk", filename: "ARUNOMATIC-0.1.6-pageflag-diag1-signed.apk", cacheControl: "no-store" }],
  [
    "/apk/aruno-clicker.apk",
    {
      key: "apk/aruno-clicker.apk",
      filename: "ARUNO_CLICKER.apk",
    },
  ],
  [
    "/apk/aruno-clicker-ver-s.apk",
    {
      key: "apk/aruno-clicker-ver-s.apk",
      filename: "ARUNO_CLICKER_ver.S.apk",
    },
  ],
  [
    "/apk/aruno-clicker-share-accessibility-test.apk",
    {
      key: "apk/aruno-clicker-share-accessibility-test.apk",
      filename: "ARUNO_CLICKER_share_accessibility_test.apk",
    },
  ],
  [
    "/apk/arunomatic.apk",
    {
      key: "apk/arunomatic.apk",
      filename: "ARUNOMATIC_SUB_v0.1.5.apk",
    },
  ],
  [
    "/apk/arunomatic-v016-test.apk",
    {
      key: "apk/arunomatic-v016-test.apk",
      filename: "ARUNOMATIC_v0.1.6-airledger2-code34.apk",
      cacheControl: "no-store",
    },
  ],
  [
    "/apk/arunomatic-air-balance-test1.apk",
    {
      key: "apk/arunomatic-air-balance-test1.apk",
      filename: "ARUNOMATIC_AIR_BALANCE_TRIAL1_CODE21.apk",
      cacheControl: "no-store",
    },
  ],
]);

const PAGE = `<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="color-scheme" content="dark">
  <meta name="theme-color" content="#111827">
  <title>ARUNO CLICKER</title>
  <style>
    :root { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #f8fafc; background: #070b14; }
    * { box-sizing: border-box; }
    body { min-height: 100dvh; margin: 0; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at top, #172554 0, #0f172a 42%, #070b14 100%); }
    main { width: min(100%, 560px); }
    h1 { margin: 0 0 8px; text-align: center; font-size: clamp(28px, 8vw, 42px); letter-spacing: .04em; }
    .lead { margin: 0 0 28px; text-align: center; color: #cbd5e1; }
    .downloads { display: grid; gap: 16px; }
    .card { padding: 20px; border: 1px solid #334155; border-radius: 18px; background: rgb(15 23 42 / 88%); box-shadow: 0 18px 50px rgb(0 0 0 / 25%); }
    .name { margin: 0 0 14px; font-size: 20px; font-weight: 750; }
    .download { display: block; width: 100%; padding: 15px 18px; border-radius: 12px; color: #082f49; background: #7dd3fc; font-weight: 800; text-align: center; text-decoration: none; }
    .download:focus-visible { outline: 3px solid #f8fafc; outline-offset: 3px; }
    .note { margin: 22px 0 0; text-align: center; color: #94a3b8; font-size: 13px; }
  </style>
</head>
<body>
  <main>
    <h1>ARUNO CLICKER</h1>
    <p class="lead">ダウンロードするアプリを選択してください</p>
    <section class="downloads" aria-label="APKダウンロード">
      <article class="card">
        <p class="name">ARUNO CLICKER</p>
        <a class="download" href="/apk/aruno-clicker.apk">ダウンロード</a>
      </article>
      <article class="card">
        <p class="name">ARUNO CLICKER ver.S</p>
        <a class="download" href="/apk/aruno-clicker-ver-s.apk">ダウンロード</a>
      </article>
      <article class="card">
        <p class="name">Redmi A3 シェア試験版</p>
        <a class="download" href="/apk/aruno-clicker-share-accessibility-test.apk">テストAPKをダウンロード</a>
      </article>
    </section>
    <p class="note">Android用APK</p>
  </main>
</body>
</html>`;


const ARUNOMATIC_QR = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0nMS4wJyBlbmNvZGluZz0nVVRGLTgnPz4KPHN2ZyB3aWR0aD0iMzdtbSIgaGVpZ2h0PSIzN21tIiB2ZXJzaW9uPSIxLjEiIHZpZXdCb3g9IjAgMCAzNyAzNyIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cGF0aCBkPSJNNCw0SDVWNUg0ek01LDRINlY1SDV6TTYsNEg3VjVINnpNNyw0SDhWNUg3ek04LDRIOVY1SDh6TTksNEgxMFY1SDl6TTEwLDRIMTFWNUgxMHpNMTIsNEgxM1Y1SDEyek0xNSw0SDE2VjVIMTV6TTE2LDRIMTdWNUgxNnpNMTcsNEgxOFY1SDE3ek0xOCw0SDE5VjVIMTh6TTIyLDRIMjNWNUgyMnpNMjQsNEgyNVY1SDI0ek0yNiw0SDI3VjVIMjZ6TTI3LDRIMjhWNUgyN3pNMjgsNEgyOVY1SDI4ek0yOSw0SDMwVjVIMjl6TTMwLDRIMzFWNUgzMHpNMzEsNEgzMlY1SDMxek0zMiw0SDMzVjVIMzJ6TTQsNUg1VjZINHpNMTAsNUgxMVY2SDEwek0xMiw1SDEzVjZIMTJ6TTEzLDVIMTRWNkgxM3pNMTUsNUgxNlY2SDE1ek0xNiw1SDE3VjZIMTZ6TTE3LDVIMThWNkgxN3pNMTgsNUgxOVY2SDE4ek0yMCw1SDIxVjZIMjB6TTIzLDVIMjRWNkgyM3pNMjYsNUgyN1Y2SDI2ek0zMiw1SDMzVjZIMzJ6TTQsNkg1VjdINHpNNiw2SDdWN0g2ek03LDZIOFY3SDd6TTgsNkg5VjdIOHpNMTAsNkgxMVY3SDEwek0xMiw2SDEzVjdIMTJ6TTEzLDZIMTRWN0gxM3pNMTYsNkgxN1Y3SDE2ek0xNyw2SDE4VjdIMTd6TTE4LDZIMTlWN0gxOHpNMTksNkgyMFY3SDE5ek0yNCw2SDI1VjdIMjR6TTI2LDZIMjdWN0gyNnpNMjgsNkgyOVY3SDI4ek0yOSw2SDMwVjdIMjl6TTMwLDZIMzFWN0gzMHpNMzIsNkgzM1Y3SDMyek00LDdINVY4SDR6TTYsN0g3VjhINnpNNyw3SDhWOEg3ek04LDdIOVY4SDh6TTEwLDdIMTFWOEgxMHpNMTQsN0gxNVY4SDE0ek0xNSw3SDE2VjhIMTV6TTE2LDdIMTdWOEgxNnpNMTcsN0gxOFY4SDE3ek0xOSw3SDIwVjhIMTl6TTIwLDdIMjFWOEgyMHpNMjEsN0gyMlY4SDIxek0yMyw3SDI0VjhIMjN6TTI0LDdIMjVWOEgyNHpNMjYsN0gyN1Y4SDI2ek0yOCw3SDI5VjhIMjh6TTI5LDdIMzBWOEgyOXpNMzAsN0gzMVY4SDMwek0zMiw3SDMzVjhIMzJ6TTQsOEg1VjlINHpNNiw4SDdWOUg2ek03LDhIOFY5SDd6TTgsOEg5VjlIOHpNMTAsOEgxMVY5SDEwek0xMiw4SDEzVjlIMTJ6TTEzLDhIMTRWOUgxM3pNMTQsOEgxNVY5SDE0ek0xNSw4SDE2VjlIMTV6TTIwLDhIMjFWOUgyMHpNMjIsOEgyM1Y5SDIyek0yMyw4SDI0VjlIMjN6TTI2LDhIMjdWOUgyNnpNMjgsOEgyOVY5SDI4ek0yOSw4SDMwVjlIMjl6TTMwLDhIMzFWOUgzMHpNMzIsOEgzM1Y5SDMyek00LDlINVYxMEg0ek0xMCw5SDExVjEwSDEwek0xNCw5SDE1VjEwSDE0ek0xNiw5SDE3VjEwSDE2ek0xOCw5SDE5VjEwSDE4ek0yMSw5SDIyVjEwSDIxek0yMiw5SDIzVjEwSDIyek0yNiw5SDI3VjEwSDI2ek0zMiw5SDMzVjEwSDMyek00LDEwSDVWMTFINHpNNSwxMEg2VjExSDV6TTYsMTBIN1YxMUg2ek03LDEwSDhWMTFIN3pNOCwxMEg5VjExSDh6TTksMTBIMTBWMTFIOXpNMTAsMTBIMTFWMTFIMTB6TTEyLDEwSDEzVjExSDEyek0xNCwxMEgxNVYxMUgxNHpNMTYsMTBIMTdWMTFIMTZ6TTE4LDEwSDE5VjExSDE4ek0yMCwxMEgyMVYxMUgyMHpNMjIsMTBIMjNWMTFIMjJ6TTI0LDEwSDI1VjExSDI0ek0yNiwxMEgyN1YxMUgyNnpNMjcsMTBIMjhWMTFIMjd6TTI4LDEwSDI5VjExSDI4ek0yOSwxMEgzMFYxMUgyOXpNMzAsMTBIMzFWMTFIMzB6TTMxLDEwSDMyVjExSDMxek0zMiwxMEgzM1YxMUgzMnpNMTMsMTFIMTRWMTJIMTN6TTE0LDExSDE1VjEySDE0ek0xNSwxMUgxNlYxMkgxNXpNMTcsMTFIMThWMTJIMTd6TTE4LDExSDE5VjEySDE4ek0xOSwxMUgyMFYxMkgxOXpNMjAsMTFIMjFWMTJIMjB6TTQsMTJINVYxM0g0ek03LDEySDhWMTNIN3pNOCwxMkg5VjEzSDh6TTksMTJIMTBWMTNIOXpNMTAsMTJIMTFWMTNIMTB6TTExLDEySDEyVjEzSDExek0xMiwxMkgxM1YxM0gxMnpNMTMsMTJIMTRWMTNIMTN6TTE1LDEySDE2VjEzSDE1ek0xOCwxMkgxOVYxM0gxOHpNMjAsMTJIMjFWMTNIMjB6TTIxLDEySDIyVjEzSDIxek0yNSwxMkgyNlYxM0gyNXpNMjgsMTJIMjlWMTNIMjh6TTMwLDEySDMxVjEzSDMwek0zMSwxMkgzMlYxM0gzMXpNMzIsMTJIMzNWMTNIMzJ6TTUsMTNINlYxNEg1ek02LDEzSDdWMTRINnpNNywxM0g4VjE0SDd6TTgsMTNIOVYxNEg4ek0xMiwxM0gxM1YxNEgxMnpNMTMsMTNIMTRWMTRIMTN6TTE0LDEzSDE1VjE0SDE0ek0xNSwxM0gxNlYxNEgxNXpNMTcsMTNIMThWMTRIMTd6TTE4LDEzSDE5VjE0SDE4ek0xOSwxM0gyMFYxNEgxOXpNMjIsMTNIMjNWMTRIMjJ6TTIzLDEzSDI0VjE0SDIzek0yNywxM0gyOFYxNEgyN3pNMjgsMTNIMjlWMTRIMjh6TTMwLDEzSDMxVjE0SDMwek0zMSwxM0gzMlYxNEgzMXpNNCwxNEg1VjE1SDR6TTgsMTRIOVYxNUg4ek0xMCwxNEgxMVYxNUgxMHpNMTIsMTRIMTNWMTVIMTJ6TTEzLDE0SDE0VjE1SDEzek0xNCwxNEgxNVYxNUgxNHpNMTUsMTRIMTZWMTVIMTV6TTE4LDE0SDE5VjE1SDE4ek0xOSwxNEgyMFYxNUgxOXpNMjEsMTRIMjJWMTVIMjF6TTI2LDE0SDI3VjE1SDI2ek0yNywxNEgyOFYxNUgyN3pNMzAsMTRIMzFWMTVIMzB6TTQsMTVINVYxNkg0ek01LDE1SDZWMTZINXpNNiwxNUg3VjE2SDZ6TTksMTVIMTBWMTZIOXpNMTIsMTVIMTNWMTZIMTJ6TTE2LDE1SDE3VjE2SDE2ek0xOCwxNUgxOVYxNkgxOHpNMTksMTVIMjBWMTZIMTl6TTIxLDE1SDIyVjE2SDIxek0yMiwxNUgyM1YxNkgyMnpNMjQsMTVIMjVWMTZIMjR6TTI2LDE1SDI3VjE2SDI2ek0yOSwxNUgzMFYxNkgyOXpNMzIsMTVIMzNWMTZIMzJ6TTcsMTZIOFYxN0g3ek04LDE2SDlWMTdIOHpNOSwxNkgxMFYxN0g5ek0xMCwxNkgxMVYxN0gxMHpNMTIsMTZIMTNWMTdIMTJ6TTEzLDE2SDE0VjE3SDEzek0xNCwxNkgxNVYxN0gxNHpNMTUsMTZIMTZWMTdIMTV6TTIwLDE2SDIxVjE3SDIwek0yMiwxNkgyM1YxN0gyMnpNMjMsMTZIMjRWMTdIMjN6TTI2LDE2SDI3VjE3SDI2ek0yNywxNkgyOFYxN0gyN3pNMzIsMTZIMzNWMTdIMzJ6TTcsMTdIOFYxOEg3ek04LDE3SDlWMThIOHpNOSwxN0gxMFYxOEg5ek0xMywxN0gxNFYxOEgxM3pNMTQsMTdIMTVWMThIMTR6TTE2LDE3SDE3VjE4SDE2ek0xNywxN0gxOFYxOEgxN3pNMTgsMTdIMTlWMThIMTh6TTE5LDE3SDIwVjE4SDE5ek0yMCwxN0gyMVYxOEgyMHpNMjEsMTdIMjJWMThIMjF6TTIyLDE3SDIzVjE4SDIyek0yNiwxN0gyN1YxOEgyNnpNMjcsMTdIMjhWMThIMjd6TTI4LDE3SDI5VjE4SDI4ek0yOSwxN0gzMFYxOEgyOXpNMzAsMTdIMzFWMThIMzB6TTMxLDE3SDMyVjE4SDMxek0zMiwxN0gzM1YxOEgzMnpNNCwxOEg1VjE5SDR6TTUsMThINlYxOUg1ek02LDE4SDdWMTlINnpNNywxOEg4VjE5SDd6TTgsMThIOVYxOUg4ek0xMCwxOEgxMVYxOUgxMHpNMTIsMThIMTNWMTlIMTJ6TTEzLDE4SDE0VjE5SDEzek0xNSwxOEgxNlYxOUgxNXpNMTksMThIMjBWMTlIMTl6TTIxLDE4SDIyVjE5SDIxek0yMywxOEgyNFYxOUgyM3pNMjcsMThIMjhWMTlIMjd6TTMwLDE4SDMxVjE5SDMwek0zMiwxOEgzM1YxOUgzMnpNNSwxOUg2VjIwSDV6TTcsMTlIOFYyMEg3ek05LDE5SDEwVjIwSDl6TTExLDE5SDEyVjIwSDExek0xMiwxOUgxM1YyMEgxMnpNMTMsMTlIMTRWMjBIMTN6TTE0LDE5SDE1VjIwSDE0ek0xNiwxOUgxN1YyMEgxNnpNMjIsMTlIMjNWMjBIMjJ6TTIzLDE5SDI0VjIwSDIzek0yNCwxOUgyNVYyMEgyNHpNMjUsMTlIMjZWMjBIMjV6TTI3LDE5SDI4VjIwSDI3ek0yOCwxOUgyOVYyMEgyOHpNMzAsMTlIMzFWMjBIMzB6TTMyLDE5SDMzVjIwSDMyek00LDIwSDVWMjFINHpNNSwyMEg2VjIxSDV6TTYsMjBIN1YyMUg2ek03LDIwSDhWMjFIN3pNMTAsMjBIMTFWMjFIMTB6TTExLDIwSDEyVjIxSDExek0xMiwyMEgxM1YyMUgxMnpNMTMsMjBIMTRWMjFIMTN6TTE0LDIwSDE1VjIxSDE0ek0xNSwyMEgxNlYyMUgxNXpNMTYsMjBIMTdWMjFIMTZ6TTE4LDIwSDE5VjIxSDE4ek0xOSwyMEgyMFYyMUgxOXpNMjAsMjBIMjFWMjFIMjB6TTI5LDIwSDMwVjIxSDI5ek00LDIxSDVWMjJINHpNMTQsMjFIMTVWMjJIMTR6TTE2LDIxSDE3VjIySDE2ek0xNywyMUgxOFYyMkgxN3pNMTksMjFIMjBWMjJIMTl6TTIxLDIxSDIyVjIySDIxek0yMiwyMUgyM1YyMkgyMnpNMjMsMjFIMjRWMjJIMjN6TTI0LDIxSDI1VjIySDI0ek0yOCwyMUgyOVYyMkgyOHpNMzAsMjFIMzFWMjJIMzB6TTMxLDIxSDMyVjIySDMxek00LDIySDVWMjNINHpNNSwyMkg2VjIzSDV6TTYsMjJIN1YyM0g2ek03LDIySDhWMjNIN3pNMTAsMjJIMTFWMjNIMTB6TTEyLDIySDEzVjIzSDEyek0xMywyMkgxNFYyM0gxM3pNMTQsMjJIMTVWMjNIMTR6TTE1LDIySDE2VjIzSDE1ek0xOSwyMkgyMFYyM0gxOXpNMjAsMjJIMjFWMjNIMjB6TTIyLDIySDIzVjIzSDIyek0yMywyMkgyNFYyM0gyM3pNMjUsMjJIMjZWMjNIMjV6TTI2LDIySDI3VjIzSDI2ek0yOSwyMkgzMFYyM0gyOXpNMzIsMjJIMzNWMjNIMzJ6TTQsMjNINVYyNEg0ek01LDIzSDZWMjRINXpNOSwyM0gxMFYyNEg5ek0xMSwyM0gxMlYyNEgxMXpNMTMsMjNIMTRWMjRIMTN6TTE0LDIzSDE1VjI0SDE0ek0xNSwyM0gxNlYyNEgxNXpNMTYsMjNIMTdWMjRIMTZ6TTE4LDIzSDE5VjI0SDE4ek0xOSwyM0gyMFYyNEgxOXpNMjMsMjNIMjRWMjRIMjN6TTI0LDIzSDI1VjI0SDI0ek0yNSwyM0gyNlYyNEgyNXpNMjcsMjNIMjhWMjRIMjd6TTI5LDIzSDMwVjI0SDI5ek0zMCwyM0gzMVYyNEgzMHpNNCwyNEg1VjI1SDR6TTUsMjRINlYyNUg1ek03LDI0SDhWMjVIN3pNOCwyNEg5VjI1SDh6TTksMjRIMTBWMjVIOXpNMTAsMjRIMTFWMjVIMTB6TTExLDI0SDEyVjI1SDExek0xMywyNEgxNFYyNUgxM3pNMTUsMjRIMTZWMjVIMTV6TTE2LDI0SDE3VjI1SDE2ek0xNywyNEgxOFYyNUgxN3pNMTgsMjRIMTlWMjVIMTh6TTE5LDI0SDIwVjI1SDE5ek0yMSwyNEgyMlYyNUgyMXpNMjMsMjRIMjRWMjVIMjN6TTI0LDI0SDI1VjI1SDI0ek0yNSwyNEgyNlYyNUgyNXpNMjYsMjRIMjdWMjVIMjZ6TTI3LDI0SDI4VjI1SDI3ek0yOCwyNEgyOVYyNUgyOHpNMjksMjRIMzBWMjlIMjl6TTMwLDI0SDMxVjI1SDMwek0zMSwyNEgzMlYyNUgzMXpNMTIsMjVIMTNWMjZIMTJ6TTEzLDI1SDE0VjI2SDEzek0xNCwyNUgxNVYyNkgxNHpNMTYsMjVIMTdWMjZIMTZ6TTE4LDI1SDE5VjI2SDE4ek0xOSwyNUgyMFYyNkgxOXpNMjEsMjVIMjJWMjZIMjF6TTIyLDI1SDIzVjI2SDIyek0yNCwyNUgyNVYyNkgyNHpNMjgsMjVIMjlWMjZIMjh6TTI5LDI1SDMwVjI2SDI5ek00LDI2SDVWMjdINHpNNSwyNkg2VjI3SDV6TTYsMjZIN1YyN0g2ek03LDI2SDhWMjdIN3pNOCwyNkg5VjI3SDh6TTksMjZIMTBWMjdIOXpNMTAsMjZIMTFWMjdIMTB6TTEyLDI2SDEzVjI3SDEyek0xNCwyNkgxNVYyN0gxNHpNMTUsMjZIMTZWMjdIMTV6TTE2LDI2SDE3VjI3SDE2ek0xOCwyNkgxOVYyN0gxOHpNMTksMjZIMjBWMjdIMTl6TTIwLDI2SDIxVjI3SDIwek0yMywyNkgyNFYyN0gyM3pNMjQsMjZIMjVWMjdIMjR6TTI2LDI2SDI3VjI3SDI2ek0yOCwyNkgyOVYyN0gyOHpNMjksMjZIMzBWMjdIMjl6TTQsMjdINVYyOEg0ek0xMCwyN0gxMVYyOEgxMHpNMTIsMjdIMTNWMjhIMTJ6TTE4LDI3SDE5VjI4SDE4ek0yMCwyN0gyMVYyOEgyMHpNMjEsMjdIMjJWMjhIMjF6TTIzLDI3SDI0VjI4SDIzek0yNCwyN0gyNVYyOEgyNHpNMjgsMjdIMjlWMjhIMjh6TTMyLDI3SDMzVjI4SDMyek00LDI4SDVWMjlINHpNNiwyOEg3VjI5SDZ6TTcsMjhIOFYyOUg3ek04LDI4SDlWMjlIOHpNMTAsMjhIMTFWMjlIMTB6TTEyLDI4SDEzVjI5SDEyek0xNCwyOEgxNVYyOUgxNHpNMTUsMjhIMTZWMjlIMTV6TTE2LDI4SDE3VjI5SDE2ek0xNywyOEgxOFYyOUgxN3pNMTgsMjRIMTlWMjlIMTh6TTE5LDI4SDIwVjI5SDE5ek0yMCwyOEgyMVYyOUgyMHpNMjMsMjhIMjRWMjlIMjN6TTI0LDI4SDI1VjI5SDI0ek0yNSwyOEgyNlYyOUgyNXpNMjYsMjhIMjdWMjlIMjZ6TTI3LDI4SDI4VjI5SDI3ek0yOCwyOEgyOVYyOUgyOHpNMjksMjhIMzBWMjlIMjl6TTMxLDI4SDMyVjI5SDMxek00LDI5SDVWMzBINHpNNiwyOUg3VjMwSDZ6TTcsMjlIOFYzMEg3ek04LDI5SDlWMzBIOHpNMTAsMjlIMTFWMzBIMTB6TTEyLDI5SDEzVjMwSDEyek0xMywyOUgxNFYzMEgxM3pNMTUsMjlIMTZWMzBIMTV6TTE2LDI5SDE3VjMwSDE2ek0xOSwyOUgyMFYzMEgxOXpNMjAsMjlIMjFWMzBIMjB6TTIxLDI5SDIyVjMwSDIxek0yMiwyOUgyM1YzMEgyMnpNMjMsMjlIMjRWMzBIMjN6TTI1LDI5SDI2VjMwSDI1ek0zMiwyOUgzM1YzMEgzMnpNNCwzMEg1VjMxSDR6TTYsMzBIN1YzMUg2ek03LDMwSDhWMzFIN3pNOCwzMEg5VjMxSDh6TTEwLDMwSDExVjMxSDEwek0xMywzMEgxNFYzMUgxM3pNMTUsMzBIMTZWMzFIMTV6TTE2LDMwSDE3VjMxSDE2ek0xNywzMEgxOFYzMUgxN3pNMTgsMzBIMTlWMzFIMTh6TTE5LDMwSDIwVjMxSDE5ek0yNCwzMEgyNVYzMUgyNHpNMjUsMzBIMjZWMzFIMjV6TTI3LDMwSDI4VjMxSDI3ek0yOCwzMEgyOVYzMUgyOHpNMzAsMzBIMzFWMzFIMzB6TTMxLDMwSDMyVjMxSDMxek0zMiwzMEgzM1YzMUgzMnpNNCwzMUg1VjMySDR6TTEwLDMxSDExVjMySDEwek0xMywzMUgxNFYzMkgxM3pNMTUsMzFIMTZWMzJIMTV6TTE2LDMxSDE3VjMySDE2ek0xOSwzMUgyMFYzMkgxOXpNMjUsMzFIMjZWMzJIMjV6TTI2LDMxSDI3VjMySDI2ek0yOCwzMUgyOVYzMkgyOHpNMjksMzFIMzBWMzJIMjl6TTMwLDMxSDMxVjMySDMwek0zMiwzMUgzM1YzMkgzMnpNNCwzMkg1VjMzSDR6TTUsMzJINlYzM0g1ek02LDMySDdWMzNINnpNNywzMkg4VjMzSDd6TTgsMzJIOVYzM0g4ek05LDMySDEwVjMzSDl6TTEwLDMySDExVjMzSDEwek0xMiwzMkgxM1YzM0gxMnpNMTMsMzJIMTRWMzNIMTN6TTE4LDMySDE5VjMzSDE4ek0yMiwzMkgyM1YzM0gyMnpNMjQsMzJIMjVWMzNIMjR6TTI1LDMySDI2VjMzSDI1ek0yOCwzMkgyOVYzM0gyOHoiIGlkPSJxci1wYXRoIiBmaWxsPSIjMDAwMDAwIiBmaWxsLW9wYWNpdHk9IjEiIGZpbGwtcnVsZT0ibm9uemVybyIgc3Ryb2tlPSJub25lIi8+PC9zdmc+";

const ARUNOMATIC_PAGE = `<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <meta name="color-scheme" content="dark">
  <meta name="theme-color" content="#111827">
  <title>ARUNOMATIC サブ端末用</title>
  <style>
    :root { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #f8fafc; background: #070b14; }
    * { box-sizing: border-box; }
    body { min-height:100dvh; margin:0; display:grid; place-items:center; padding:22px; background:radial-gradient(circle at top,#172554 0,#0f172a 42%,#070b14 100%); }
    main { width:min(100%,520px); }
    .card { padding:24px; border:1px solid #334155; border-radius:20px; background:rgb(15 23 42 / 92%); box-shadow:0 18px 50px rgb(0 0 0 / 28%); text-align:center; }
    h1 { margin:0; font-size:clamp(30px,9vw,44px); letter-spacing:.05em; }
    .sub { margin:8px 0 4px; font-size:20px; font-weight:800; }
    .version { margin:0 0 18px; color:#94a3b8; font-size:14px; }
    .qr { width:min(72vw,280px); aspect-ratio:1; background:white; padding:10px; border-radius:14px; margin:0 auto 18px; display:block; }
    .versions { display:grid; gap:12px; margin-top:8px; }
    .version-card { padding:14px; border:1px solid #334155; border-radius:14px; background:rgb(2 6 23 / 45%); text-align:left; }
    .version-card strong { display:block; font-size:17px; margin-bottom:4px; }
    .version-card small { display:block; color:#94a3b8; margin-bottom:10px; line-height:1.5; }
    .download { display:block; width:100%; padding:14px 16px; border-radius:12px; color:#082f49; background:#7dd3fc; font-size:17px; font-weight:850; text-align:center; text-decoration:none; }
    .download.test { background:#fde68a; color:#422006; }
    .download:focus-visible { outline:3px solid #f8fafc; outline-offset:3px; }
    .guide { margin:18px 0 0; color:#cbd5e1; font-size:14px; line-height:1.7; }
    .fixed { margin:12px 0 0; color:#94a3b8; font-size:12px; line-height:1.6; }
    code { color:#bae6fd; overflow-wrap:anywhere; }
  </style>
</head>
<body>
  <main>
    <section class="card">
      <h1>ARUNOMATIC</h1>
      <p class="sub">端末03・05・06・99 共通</p>
      <p class="version">v0.1.6-airledger2 / code 34</p>
      <img class="qr" src="${ARUNOMATIC_QR}" alt="ARUNOMATIC サブ端末用ダウンロードページのQRコード">
      <div class="versions">
        <div class="version-card">
          <strong>最新版 v0.1.6-airledger2 / code 34</strong>
          <small>AIR01〜40の残高確認後、ユーザー番号を自動取得します。中央管理台帳へ自動同期し、端末99で集約・検索できます。一覧は登録済みだけを表示します。重複はユーザー番号で判定し、カード情報は自端末だけに暗号化保存します。カードは後から登録・変更でき、他端末へ送るのは登録状態だけです。ARUNOMATIC内の指紋認証は不要です。</small>
          <a class="download" href="/apk/arunomatic-v016-test.apk">最新版 code 34 をダウンロード</a>
        </div>
      </div>
      <p class="guide">端末03・05・06・99へ同じAPKを上書きしてください。既存データを保つためアンインストールは不要です。初回だけ「AIR端末を認証」で既存の端末認証を連携します。登録・取得・変更したAIR情報は自動送信され、端末99の「中央台帳を同期・取得」で集約を更新できます。今後はアプリ内の更新通知・「更新する」からこのページを開けます。</p>
      <p class="fixed">このQRは固定です。今後APKを更新しても入口URLは<br><code>download.aruno-id.com/arunomatic</code> のままです。</p>
    </section>
  </main>
</body>
</html>`;

const ARUNOMATIC_PAGE36 = ARUNOMATIC_PAGE
  .replaceAll('v0.1.6-airledger2 / code 34','v0.1.7-devicecontrol2 / code 36（実機試験版）')
  .replaceAll('最新版 code 34 をダウンロード','code 36 をダウンロード')
  .replaceAll('href="/apk/arunomatic-v016-test.apk"','href="/apk/arunomatic-code36.apk"')
  .replace('<p class="guide">','<p class="guide">code36は実機未確認です。まず端末03だけで試し、端末通信はOFFのままにしてください。日時のJST表示、重複グループ表示、AIR開始画面の復旧を追加しました。STATUS／COMMANDはサーバー反映後に試験します。</p><p class="guide">')
  .replace('端末03・05・06・99へ同じAPKを上書きしてください。','既存アプリをアンインストールせず上書きしてください。')
  .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-v016-test.apk">実機確認済みcode34を保存</a><br>code36から低い版への上書きは通常できません。戻すためにアンインストールしないでください。</p><p class="fixed">');

const ARUNOMATIC_PAGE37 = ARUNOMATIC_PAGE36
  .replaceAll('v0.1.7-devicecontrol2 / code 36','v0.1.7-air-edit1 / code 37')
  .replaceAll('code 36 をダウンロード','code 37 をダウンロード')
  .replaceAll('href="/apk/arunomatic-code36.apk"','href="/apk/arunomatic-code37.apk"')
  .replace('code36は実機未確認です。まず端末03だけで試し、端末通信はOFFのままにしてください。日時のJST表示、重複グループ表示、AIR開始画面の復旧を追加しました。STATUS／COMMANDはサーバー反映後に試験します。','code37の編集操作は実機未確認です。まず端末03で試し、端末通信はOFFのままにしてください。自端末AIR一覧に1件削除・上書き・番号訂正を追加。残高取得はcode34式へ戻しました。取得できないAIRは手動で残高画面へ戻して再確認してください。STATUS／COMMANDはサーバー反映後に試験します。')
  .replace('code36から低い版','code37から低い版')
  .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code36.apk">code36のテスト用APKを保存</a><br>code36のテスト中は結果を記録してからcode37へ更新してください。</p><p class="fixed">');

const ARUNOMATIC_PAGE38 = ARUNOMATIC_PAGE37
  .replaceAll('v0.1.7-air-edit1 / code 37','v0.1.7-air-display1 / code 38')
  .replaceAll('code 37 をダウンロード','code 38 をダウンロード')
  .replaceAll('href="/apk/arunomatic-code37.apk"','href="/apk/arunomatic-code38.apk"')
  .replace('code37の編集操作は実機未確認です。','code38の表示修正は実機未確認です。右端ボタンを縦並びにし、中断・失敗時も前回残高を表示します。')
  .replace('code37から低い版','code38から低い版')
  .replace('code36のテスト中は結果を記録してからcode37へ更新してください。','code37で一覧が空白になる場合はcode38へ上書きし、再取得前の一覧を確認してください。')
  .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code37.apk">旧code37 APKを保存</a></p><p class="fixed">');

const ARUNOMATIC_PAGE39 = ARUNOMATIC_PAGE
 .replaceAll('v0.1.6-airledger2 / code 34','v0.1.8-device-test1 / code 39（動作テスト版）')
 .replaceAll('最新版 code 34 をダウンロード','code 39 をダウンロード')
 .replaceAll('href="/apk/arunomatic-v016-test.apk"','href="/apk/arunomatic-code39.apk"')
 .replace('<p class="guide">','<p class="guide">新しいCOMMAND操作は実機未確認です。まず端末03だけで試してください。既存AIR残高確認・途中中断時の前回値保持・右端縦ボタン・削除機能はcode38から維持します。</p><p class="guide">端末通信をONにし、「端末03：軽い動作テストを10分間有効にする」を押します。AccessibilityをON、画面を点灯・解除し、AIR確認・Taskを終了してから、中央の端末管理画面で1件ずつ送ってください。疎通確認、STATUS再送、テスト表示、ホーム移動、現在ページ確認を試せます。終了時は動作テストを終了してください。</p><p class="guide">')
 .replace('端末03・05・06・99へ同じAPKを上書きしてください。','まず端末03へ上書きしてください。アンインストール・データ削除は不要です。')
 .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code38.apk">実機確認済みcode38を保存</a><br><a href="/apk/arunomatic-v016-test.apk">基準code34を保存</a><br>低いversionCodeへの上書きは通常できません。戻すためのアンインストールはしないでください。</p><p class="fixed">');

const ARUNOMATIC_PAGE41 = ARUNOMATIC_PAGE
 .replaceAll('v0.1.6-airledger2 / code 34','v0.1.9-winning-share1 / code 41')
 .replaceAll('最新版 code 34 をダウンロード','最新版 code 41 をダウンロード')
 .replaceAll('href="/apk/arunomatic-v016-test.apk"','href="/apk/arunomatic-code41.apk"')
 .replaceAll('端末03・05・06・99 共通','サブ端末用')
 .replace(/<small>AIR01[\s\S]*?<\/small>/, '<small>当選URL・コードをAndroid共有から転送できます。端末03で上書き更新・Nova転送・既存機能を確認済みです。当選URL管理での受信・保存も利用者確認済みです。</small>')
 .replace(/<p class="guide">[\s\S]*?<\/p>/, '<p class="guide">既存ARUNOMATICへ上書きしてください。アンインストールは不要です。当選URL送信には各端末で送信専用API・端末トークンの設定が必要です。更新後に端末番号とNova転送を確認してください。</p>')
 .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code39.apk">旧code39 APKを保存</a><br><a href="/apk/arunomatic-code38.apk">旧code38 APKを保存</a><br><a href="/apk/arunomatic-v016-test.apk">基準code34 APKを保存</a><br>低いversionCodeへの上書きは通常できません。</p><p class="fixed">');

const ARUNOMATIC_PAGE42 = ARUNOMATIC_PAGE41
 .replaceAll('v0.1.9-winning-share1 / code 41','v0.1.9-winning-share2 / code 42')
 .replaceAll('最新版 code 41','最新版 code 42')
 .replaceAll('href="/apk/arunomatic-code41.apk"','href="/apk/arunomatic-code42.apk"')
 .replace('当選URL・コードをAndroid共有から転送できます。端末03で上書き更新・Nova転送・既存機能を確認済みです。当選URL管理での受信・保存も利用者確認済みです。','転送・新規・重複・エラー件数を表示します。リンク共有のプレビュー画像による混在エラーを修正しました。Nova転送・既存機能の自動回帰テストと既存署名との一致を確認済みです。')
 .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code41.apk">旧code41 APKを保存</a></p><p class="fixed">');

const ARUNOMATIC_PAGE43 = ARUNOMATIC_PAGE42
 .replaceAll('v0.1.9-winning-share2 / code 42','v0.1.9-winning-share3 / code 43')
 .replaceAll('最新版 code 42','最新版 code 43')
 .replaceAll('href="/apk/arunomatic-code42.apk"','href="/apk/arunomatic-code43.apk"')
 .replace('転送・新規・重複・エラー件数を表示します。リンク共有のプレビュー画像による混在エラーを修正しました。','転送結果を大きく表示します。成功・重複は青色、エラーは赤色です。共有からの送信が完了すると2.5秒後に共有画面を閉じます。「表示を残す」も選択できます。')
 .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code42.apk">旧code42 APKを保存</a></p><p class="fixed">');
const ARUNOMATIC_PAGE45 = ARUNOMATIC_PAGE43
 .replaceAll('v0.1.9-winning-share3 / code 43','v0.2.1-control-status / code 45')
 .replaceAll('最新版 code 43','最新版 code 45')
 .replaceAll('href="/apk/arunomatic-code43.apk"','href="/apk/arunomatic-code45.apk"')
 .replace('転送結果を大きく表示します。成功・重複は青色、エラーは赤色です。共有からの送信が完了すると2.5秒後に共有画面を閉じます。「表示を残す」も選択できます。','15台CONTROL、端末STATUS、画面ON／OFFに対応。通信トグルでCONTROL受付を切り替えます。完了ボタンを更新するボタンの下へ移動しました。上書き更新後に端末通信・Accessibilityを確認してください。')
 .replace('<p class="fixed">','<p class="guide"><a href="/apk/arunomatic-code43.apk">旧code43 APKを保存</a></p><p class="fixed">');
const ARUNOMATIC_PAGE46 = ARUNOMATIC_PAGE45
 .replaceAll('v0.2.1-control-status / code 45','v0.2.2-control-layout / code 46')
 .replaceAll('最新版 code 45','最新版 code 46')
 .replaceAll('href="/apk/arunomatic-code45.apk"','href="/apk/arunomatic-code46.apk"')
 .replace('15台CONTROL、端末STATUS、画面ON／OFFに対応。通信トグルでCONTROL受付を切り替えます。完了ボタンを更新するボタンの下へ移動しました。上書き更新後に端末通信・Accessibilityを確認してください。','端末STATUSを3列表示に整理。確認済み移動履歴、画面・ロック操作、選択端末のCONTROLに対応。アプリのタイトルと完了操作を固定しました。PIN・指紋ロックは端末で解除してください。');
const ARUNOMATIC_PAGE47 = ARUNOMATIC_PAGE46
 .replaceAll('v0.2.2-control-layout / code 46','v0.2.3-dock-position / code 47')
 .replaceAll('最新版 code 46','最新版 code 47')
 .replaceAll('href="/apk/arunomatic-code46.apk"','href="/apk/arunomatic-code47.apk"')
 .replace('端末STATUSを3列表示に整理。確認済み移動履歴、画面・ロック操作、選択端末のCONTROLに対応。アプリのタイトルと完了操作を固定しました。PIN・指紋ロックは端末で解除してください。','CONTROLのTaskをドック右側3枠の位置で起動します。右からRight・Center・Leftの順です。Task名の入力と設定ボタンは不要になりました。位置を確定できない場合は起動せず停止します。');
async function arunomaticPageResponse(env) {
  let page=ARUNOMATIC_PAGE;
  try {
    const response=await latestManifest(new Request('https://download.aruno-id.com/arunomatic/latest.json'),env);
    const m=await response.json();
    if(response.ok&&m.version_code===36&&m.version_name==='v0.1.7-devicecontrol2'&&
       m.sha256==='21eda0634986500afc3744f50487628a1858714fc25a6ee149bd3c9e24e3f479'&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code36.apk')page=ARUNOMATIC_PAGE36;
    if(response.ok&&m.version_code===37&&m.version_name==='v0.1.7-air-edit1'&&
       m.sha256==='adc6e54fa64197ea015f544f002a2d9e4e2e51e25c4dbc36a88aa7a43b3bdb83'&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code37.apk')page=ARUNOMATIC_PAGE37;
    if(response.ok&&m.version_code===38&&m.version_name==='v0.1.7-air-display1'&&
       m.sha256==='8fcb498e31f2e32e8cb9c79d3a29ea07e5852b43ffb5f6e02ecbdbd109e6df45'&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code38.apk')page=ARUNOMATIC_PAGE38;
    if(response.ok&&m.version_code===39&&m.version_name==='v0.1.8-device-test1'&&
       m.sha256==='1eccb60e74ac3adfb48c5e97106a83cfc3aab802df293cac76e1f385ab4db0b5'&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code39.apk')page=ARUNOMATIC_PAGE39;
    if(response.ok&&m.version_code===41&&m.version_name==='v0.1.9-winning-share1'&&
       m.sha256===CODE41_SHA&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code41.apk')page=ARUNOMATIC_PAGE41;
    if(response.ok&&m.version_code===42&&m.version_name==='v0.1.9-winning-share2'&&
       m.sha256===CODE42_SHA&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code42.apk')page=ARUNOMATIC_PAGE42;
    if(response.ok&&m.version_code===43&&m.version_name==='v0.1.9-winning-share3'&&
       m.sha256===CODE43_SHA&&
       m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code43.apk')page=ARUNOMATIC_PAGE43;
    if(response.ok&&m.version_code===45&&m.version_name==='v0.2.1-control-status'&&m.sha256===CODE45_SHA&&m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code45.apk')page=ARUNOMATIC_PAGE45;
    if(response.ok&&m.version_code===46&&m.version_name==='v0.2.2-control-layout'&&m.sha256===CODE46_SHA&&m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code46.apk')page=ARUNOMATIC_PAGE46;
    if(response.ok&&m.version_code===47&&m.version_name==='v0.2.3-dock-position'&&m.sha256===CODE47_SHA&&m.apk_url==='https://download.aruno-id.com/apk/arunomatic-code47.apk')page=ARUNOMATIC_PAGE47;
  }catch{}
  // Show the trial only after the verified APK has been uploaded. Do not change latest.json.
  try {
    const candidate = await env.APK_BUCKET.head(CODE41_KEY);
    if (page !== ARUNOMATIC_PAGE41 && page !== ARUNOMATIC_PAGE42 && page !== ARUNOMATIC_PAGE43 && page !== ARUNOMATIC_PAGE45 && page !== ARUNOMATIC_PAGE46 && page !== ARUNOMATIC_PAGE47 && candidate?.customMetadata?.sha256 === CODE41_SHA) {
      page = page.replace('<div class="versions">', `<div class="versions">
        <div class="version-card">
          <strong>実機確認用 v0.1.9-winning-share1 / code 41</strong>
          <small>当選URL・コード共有の送信口を追加しました。まず1台でNova転送・AIR Wallet・端末設定などの既存機能を確認してください。Vaultへの送信は送信専用API・端末トークンの準備後に確認します。</small>
          <a class="download test" href="/apk/arunomatic-code41.apk">code 41 をダウンロード</a>
          <small>通常版ARUNOMATICへ上書きしてください。アンインストールは不要です。更新通知への切り替えは実機確認後です。</small>
        </div>`);
    }
  } catch {}
  // Candidate availability never selects the stable version or update manifest.
  try {
    const candidate = await env.APK_BUCKET.head(CODE44_CANDIDATE_KEY);
    if (page !== ARUNOMATIC_PAGE45 && page !== ARUNOMATIC_PAGE46 && page !== ARUNOMATIC_PAGE47 && candidate?.customMetadata?.sha256 === CODE44_CANDIDATE_SHA) {
      page = page.replace('<div class="versions">', `<div class="versions">
        <div class="version-card">
          <strong>実機試験用 code 44 候補</strong>
          <small>v0.2.0-control-test1 ／ 端末01〜15の一括CONTROL試験用。1Task・対象グループ・対象ページを指定し、端末ごとに独立実行します。</small>
          <small style="color:#b91c1c">本番運用未承認・15台実機試験未実施。フローティング即時停止は未整備です。停止手段の確認前に15台実行を開始しないでください。</small>
          <a class="download test" href="/apk/arunomatic-code44-candidate.apk">code 44 候補をダウンロード</a>
          <small>既存署名の通常版へ上書き更新できます。公開安定版・自動更新通知はcode 43を維持しています。</small>
        </div>`);
    }
  } catch {}
  return new Response(page, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      "Referrer-Policy": "no-referrer",
    },
  });
}

function pageResponse() {
  return new Response(PAGE, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=300",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
      "Referrer-Policy": "no-referrer",
    },
  });
}

async function apkResponse(request, env, download) {
  const object = await env.APK_BUCKET.get(download.key);
  if (object === null) {
    return new Response("APK is not available.", { status: 404 });
  }

  if (download.expectedSha && object.customMetadata?.sha256 !== download.expectedSha) {
    return new Response("Candidate APK verification failed.", {status:503,headers:{"Cache-Control":"no-store"}});
  }
  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", APK_CONTENT_TYPE);
  const filename = /^[A-Za-z0-9._-]+$/.test(object.customMetadata?.filename || "") ? object.customMetadata.filename : download.filename;
  headers.set("Content-Disposition", `attachment; filename="${filename}"`);
  headers.set("Content-Length", String(object.size));
  headers.set("Cache-Control", download.cacheControl || "public, max-age=60, must-revalidate");
  headers.set("ETag", object.httpEtag);
  headers.set("X-Content-Type-Options", "nosniff");
  const sha256 = object.customMetadata?.sha256;
  if (sha256) headers.set("X-Checksum-SHA256", sha256);

  if (request.method === "HEAD") {
    return new Response(null, { headers });
  }
  return new Response(object.body, { headers });
}

async function latestManifest(request, env) {
  const object = await env.APK_BUCKET.get("releases/arunomatic/latest.json");
  if (!object) return Response.json({error:"UPDATE_NOT_AVAILABLE"},{status:503,headers:{"cache-control":"no-store"}});
  try {
    const manifest = JSON.parse(await object.text());
    if (manifest.package !== "com.aruno.arunomatic" || !Number.isInteger(manifest.version_code) || manifest.version_code < 1 ||
        manifest.download_page !== "https://download.aruno-id.com/arunomatic" ||
        !/^[a-f0-9]{64}$/.test(manifest.sha256 || "")) throw new Error("invalid manifest");
    const response = Response.json(manifest,{headers:{"cache-control":"no-store","x-content-type-options":"nosniff"}});
    return request.method === "HEAD" ? new Response(null,{headers:response.headers}) : response;
  } catch { return Response.json({error:"UPDATE_NOT_AVAILABLE"},{status:503,headers:{"cache-control":"no-store"}}); }
}

async function testManifest(request,env) {
  try {
    const object=await env.APK_BUCKET.get("releases/arunomatic/test.json");
    if(!object)throw new Error("missing");
    const m=JSON.parse(await object.text());
    if(m.channel!=="test"||m.package!=="com.aruno.arunomatic"||m.version_code!==48||
       !/^[a-f0-9]{40}$/.test(m.commit||"")||!/^[a-f0-9]{64}$/.test(m.sha256||"")||
       m.apk_url!=="https://download.aruno-id.com/apk/arunomatic-test-code48-"+m.commit+".apk")throw new Error("invalid");
    const response=Response.json(m,{headers:{"cache-control":"no-store","x-content-type-options":"nosniff"}});
    return request.method==="HEAD"?new Response(null,{headers:response.headers}):response;
  }catch{return Response.json({error:"TEST_NOT_AVAILABLE"},{status:503,headers:{"cache-control":"no-store"}});}
}
async function testApk(request,env,commit,expectedSha) {
 return apkResponse(request,env,{key:"candidates/arunomatic/code48/"+commit+"/ARUNOMATIC-code48-test.apk",filename:"ARUNOMATIC-code48-test.apk",cacheControl:"no-store",expectedSha});
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", {
        status: 405,
        headers: { Allow: "GET, HEAD" },
      });
    }

    if (url.pathname === "/" || url.pathname === "/index.html") {
      return request.method === "HEAD"
        ? new Response(null, { headers: pageResponse().headers })
        : pageResponse();
    }

    if (url.pathname === "/arunomatic/latest.json") return latestManifest(request, env);
    if (url.pathname === "/arunomatic/test.json") return testManifest(request,env);
    if (url.pathname === "/apk/arunomatic-test.apk") {
      const response=await testManifest(new Request("https://download.aruno-id.com/arunomatic/test.json"),env);
      if(!response.ok)return response;
      const m=await response.json();return testApk(request,env,m.commit,m.sha256);
    }
    const testPath=url.pathname.match(/^\/apk\/arunomatic-test-code48-([a-f0-9]{40})\.apk$/);
    if(testPath)return testApk(request,env,testPath[1]);


    if (url.pathname === "/arunomatic" || url.pathname === "/arunomatic/") {
      return request.method === "HEAD"
        ? new Response(null, { headers: (await arunomaticPageResponse(env)).headers })
        : arunomaticPageResponse(env);
    }

    const download = DOWNLOADS.get(url.pathname);
    if (download) return apkResponse(request, env, download);

    return new Response("Not Found", { status: 404 });
  },
};




