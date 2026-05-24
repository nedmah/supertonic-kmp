import SwiftUI
import SupertonicKMP
import AVFoundation

struct ContentView: View {

    @State private var statusText = "Press Download to get started"
    @State private var inputText = "Привет! Это тест синтеза речи."
    @State private var isDownloading = false
    @State private var isReady = false
    @State private var isGenerating = false
    @State private var progress: Float = 0

    @State private var audioPlayer: AVAudioPlayer?

    private let tts: SupertonicTts = {
        let docsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let storageDir = docsDir.appendingPathComponent("supertonic").path
        let config = SupertonicConfig(
            storageDir: storageDir,
            inferenceSteps: 8,
            defaultLang: "ru",
            defaultVoice: SupertonicVoice.m1,
            defaultSpeed: 1.0,
            huggingFaceBaseUrl: SupertonicConfig.companion.DEFAULT_HF_URL
        )
        return SupertonicTts(config: config)
    }()

    var body: some View {
        VStack(spacing: 16) {

            Text("Supertonic TTS")
                .font(.headline)

            Text(statusText)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if isDownloading {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
            }

            Button(isReady ? "Downloaded ✓" : "Download Models (~280MB)") {
                Task { await download() }
            }
            .disabled(isDownloading || isReady)
            .buttonStyle(.borderedProminent)

            TextField("Text to synthesize", text: $inputText, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(3...6)
                .disabled(isGenerating)

            Button(isGenerating ? "Generating..." : "Speak") {
                Task { await speak() }
            }
            .disabled(!isReady || isGenerating || inputText.isEmpty)
            .buttonStyle(.borderedProminent)

            if isGenerating {
                Button("Stop") {
                    tts.stop()
                    isGenerating = false
                }
                .buttonStyle(.bordered)
                .tint(.red)
            }
        }
        .padding(24)
        .onAppear {
            isReady = tts.downloadState.value is DownloadState.Ready
            if isReady { statusText = "Models ready!" }
        }
    }

    private func download() {
        isDownloading = true
        tts.startDownload(
            onProgress: { fraction, formatted in
                DispatchQueue.main.async {
                    self.progress = fraction.floatValue
                    self.statusText = "Downloading... \(formatted)"
                }
            },
            onReady: {
                DispatchQueue.main.async {
                    self.isDownloading = false
                    self.isReady = true
                    self.statusText = "Models ready!"
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    self.isDownloading = false
                    self.statusText = "Error: \(message)"
                }
            }
        )
    }

    private func speak() async {
        isGenerating = true
        statusText = "Generating..."
        let result = try? await tts.generate(
            text: inputText,
            lang: "ru",
            voice: SupertonicVoice.m1,
            speed: 1.0
        )
        await MainActor.run {
            switch result {
            case let success as GenerateResult.Success:
                statusText = "Done! \(success.durationMs)ms"
                playWav(success.wav)
            case let error as GenerateResult.Error:
                statusText = "Error: \(error.cause.message ?? "unknown")"
            default:
                statusText = "No result"
            }
            isGenerating = false
        }
    }

    private func playWav(_ kotlinBytes: KotlinByteArray) {
        let count = Int(kotlinBytes.size)
        var data = Data(count: count)
        data.withUnsafeMutableBytes { (ptr: UnsafeMutableRawBufferPointer) in
            for i in 0..<count {
                ptr[i] = UInt8(bitPattern: kotlinBytes.get(index: Int32(i)))
            }
        }
        do {
            audioPlayer = try AVAudioPlayer(data: data)
            audioPlayer?.play()
        } catch {
            print("[ContentView] playWav error: \(error)")
        }
    }
}
