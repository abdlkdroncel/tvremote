# TV Remote

Android TV’yi tarayıcıdan yönetmek için küçük bir Spring Boot uygulaması. Ev ağındaki televizyona ADB üzerinden bağlanır; telefon veya bilgisayardan sanal kumanda gibi kullanılır.

## Özellikler

- IP ve port ile TV’ye giriş
- Yön tuşları, OK, Home, Geri, Sessiz
- Ses seviyesi (0–100)
- Arama / yazı girişi (Klavye)
- Mobil uyumlu web arayüzü
- Docker ile ev sunucusunda çalıştırma

## Gereksinimler

- **Java 25** (yerel çalıştırma için)
- Android TV’de **ADB debugging** açık ve ağ üzerinden erişilebilir
- Uygulama ile TV **aynı yerel ağda** olmalı

> Vercel / bulut deploy bu proje için uygun değil. ADB bağlantısı ev ağındaki bir makineden yapılmalıdır.

## Hızlı başlangıç

```bash
./mvnw spring-boot:run
```

Tarayıcıda aç:

```
http://localhost:8080
```

1. TV’nin IP adresini ve ADB portunu gir (çoğu cihazda `5555`)
2. **Giriş**’e bas
3. İlk bağlantıda TV ekranında ADB izni çıkarsa onayla

### Telefondan (aynı Wi‑Fi)

1. Uygulamayı evdeki bilgisayar / sunucuda çalıştır
2. O makinenin yerel IP’sini öğren (ör. `10.22.27.50`)
3. Telefonda aç: `http://10.22.27.50:8080`

## Docker

```bash
docker compose up -d --build
```

Arayüz: `http://SUNUCU_IP:8080`

ADB anahtarları `./data/android` altında saklanır; container yeniden başlasa bile TV’de tekrar onay istemez.

Linux’ta TV’ye erişim sorun olursa host ağı kullan:

```bash
docker compose -f docker-compose.yml -f docker-compose.host.yml up -d --build
```

## Kullanım

| Kontrol | Ne yapar |
| --- | --- |
| Yön / OK | Menüde gezinme |
| Home / Geri | Ana ekran / geri |
| Ses kaydırıcısı | 0–100 arası ses |
| Sessiz | Mute |
| Klavye → Yaz | Odaklanan alana metin gönderir |
| Sil / Ara | Karakter silme / Enter |

Klavye açık değilken bilgisayar klavyesi de çalışır: yön tuşları, Enter (OK), Esc (Geri), `H` (Home), `M` (Mute).

## API

| Method | Endpoint | Açıklama |
| --- | --- | --- |
| `GET` | `/remote/api/status` | Bağlantı durumu |
| `POST` | `/remote/api/connect` | `{ "host", "port" }` ile bağlan |
| `POST` | `/remote/api/disconnect` | Bağlantıyı kes |
| `POST` | `/remote/api/key/{name}` | `UP`, `DOWN`, `LEFT`, `RIGHT`, `OK`, `BACK`, `HOME`, `MUTE`, `DEL`, `ENTER` |
| `POST` | `/remote/api/volume/{0-100}` | Ses ayarla |
| `POST` | `/remote/api/text` | `{ "text": "..." }` yaz |

Bağlantı yokken tuş / ses / yazı istekleri `401` döner.

## Proje yapısı

```
src/main/java/...     Spring API ve ADB oturumu
src/main/resources/static/   Web kumanda arayüzü
Dockerfile
docker-compose.yml
docker-compose.host.yml
```

## Notlar

- Uygulamada kimlik doğrulama yok. Yalnızca güvendiğin yerel ağda çalıştır; public internete açma.
- Türkçe karakterler bazı cihazlarda `input text` ile eksik gidebilir.
- Son kullanılan IP/port tarayıcıda saklanır.

## Lisans

Kişisel / iç kullanım için.
