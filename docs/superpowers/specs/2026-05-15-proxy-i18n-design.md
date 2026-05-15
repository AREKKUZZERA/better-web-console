# Proxy Examples and Multi-language UI Design

## Goal

Add documented reverse-proxy examples for HTTPS setups and add a language selector to the web UI.

## Scope

- Document Nginx, Caddy, and Cloudflare Tunnel examples in `README.md`.
- Keep the plugin HTTP-only; TLS is handled by the reverse proxy.
- Add UI translations for English, Russian, Chinese, Polish, German, and French.
- English is the default language.
- Persist the selected language in browser `localStorage`.
- Translate only web UI labels, buttons, hints, empty states, titles, and toast/status messages.
- Do not translate server logs, Minecraft output, player messages, or command text.

## UI Design

The existing single-file web UI remains in `src/main/resources/webconsole.html`.
Translations are stored in a small inline dictionary keyed by language code.
Static DOM strings use `data-i18n` attributes.
JS-generated strings go through a `t(key)` helper.
The language selector appears in the authenticated UI and on the login screen.

## Docs Design

`README.md` gets a `Reverse Proxy / HTTPS` section with examples for:

- Nginx with WebSocket headers and forwarded headers.
- Caddy with `reverse_proxy`.
- Cloudflare Tunnel with a local HTTP service target.

The docs recommend binding the plugin to localhost when exposed through a proxy.

## Verification

Run `mvn clean package`.
No dev server is started.
