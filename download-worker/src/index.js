const APK_CONTENT_TYPE = "application/vnd.android.package-archive";

const DOWNLOADS = new Map([
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
      filename: "ARUNOMATIC_SUB.apk",
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
    .download { display:block; width:100%; padding:16px 18px; border-radius:12px; color:#082f49; background:#7dd3fc; font-size:19px; font-weight:850; text-align:center; text-decoration:none; }
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
      <p class="sub">サブ端末用</p>
      <p class="version">v0.1.3</p>
      <img class="qr" src="${ARUNOMATIC_QR}" alt="ARUNOMATIC サブ端末用ダウンロードページのQRコード">
      <a class="download" href="/apk/arunomatic.apk">APKをダウンロード</a>
      <p class="guide">抽選用端末にインストールしてください。<br>初回だけ端末番号と8桁の登録コードで端末登録します。</p>
      <p class="fixed">このQRは固定です。今後APKを更新しても入口URLは<br><code>download.aruno-id.com/arunomatic</code> のままです。</p>
    </section>
  </main>
</body>
</html>`;

function arunomaticPageResponse() {
  return new Response(ARUNOMATIC_PAGE, {
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=60",
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

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("Content-Type", APK_CONTENT_TYPE);
  headers.set("Content-Disposition", `attachment; filename="${download.filename}"`);
  headers.set("Content-Length", String(object.size));
  headers.set("Cache-Control", "public, max-age=60, must-revalidate");
  headers.set("ETag", object.httpEtag);
  headers.set("X-Content-Type-Options", "nosniff");
  const sha256 = object.customMetadata?.sha256;
  if (sha256) headers.set("X-Checksum-SHA256", sha256);

  if (request.method === "HEAD") {
    return new Response(null, { headers });
  }
  return new Response(object.body, { headers });
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

    if (url.pathname === "/arunomatic" || url.pathname === "/arunomatic/") {
      return request.method === "HEAD"
        ? new Response(null, { headers: arunomaticPageResponse().headers })
        : arunomaticPageResponse();
    }

    const download = DOWNLOADS.get(url.pathname);
    if (download) return apkResponse(request, env, download);

    return new Response("Not Found", { status: 404 });
  },
};
