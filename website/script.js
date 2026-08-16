document.addEventListener("DOMContentLoaded", () => {
    // 0. Preloader
    const preloader = document.getElementById("preloader");
    if (preloader) {
        window.addEventListener("load", () => {
            setTimeout(() => {
                preloader.classList.add("hidden");
                setTimeout(() => {
                    preloader.style.display = "none";
                }, 600);
            }, 1000);
        });
    }

    // 1. Custom Cursor
    const cursor = document.getElementById("customCursor");
    if (cursor) {
        if (window.matchMedia("(pointer: fine)").matches) {
            document.addEventListener("mousemove", (e) => {
                cursor.style.left = e.clientX + "px";
                cursor.style.top = e.clientY + "px";
            });

            const interactiveElements = document.querySelectorAll(
                "a, button, .project-card, .value-card, .team-member"
            );
            interactiveElements.forEach((el) => {
                el.addEventListener("mouseenter", () =>
                    cursor.classList.add("hovering")
                );
                el.addEventListener("mouseleave", () =>
                    cursor.classList.remove("hovering")
                );
            });
        } else {
            cursor.style.display = "none";
        }
    }

    // 2. Particle Background
    const canvas = document.getElementById("particlesCanvas");
    if (canvas) {
        const ctx = canvas.getContext("2d");
        let particles = [];

        function resizeCanvas() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }

        resizeCanvas();
        window.addEventListener("resize", resizeCanvas);

        class Particle {
            constructor() {
                this.x = Math.random() * canvas.width;
                this.y = Math.random() * canvas.height;
                this.size = Math.random() * 2 + 1;
                this.speedX = (Math.random() - 0.5) * 0.3;
                this.speedY = (Math.random() - 0.5) * 0.3;
                this.opacity = Math.random() * 0.5 + 0.1;
            }

            update() {
                this.x += this.speedX;
                this.y += this.speedY;
                if (this.x < 0) this.x = canvas.width;
                if (this.x > canvas.width) this.x = 0;
                if (this.y < 0) this.y = canvas.height;
                if (this.y > canvas.height) this.y = 0;
            }

            draw() {
                ctx.fillStyle = `rgba(217, 48, 37, ${this.opacity})`;
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        for (let i = 0; i < 40; i++) {
            particles.push(new Particle());
        }

        function animateParticles() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            particles.forEach((p1, i) => {
                particles.slice(i + 1).forEach((p2) => {
                    const dx = p1.x - p2.x;
                    const dy = p1.y - p2.y;
                    const dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 120) {
                        ctx.strokeStyle = `rgba(217, 48, 37, ${0.1 * (1 - dist / 120)})`;
                        ctx.lineWidth = 0.5;
                        ctx.beginPath();
                        ctx.moveTo(p1.x, p1.y);
                        ctx.lineTo(p2.x, p2.y);
                        ctx.stroke();
                    }
                });
            });
            particles.forEach((p) => {
                p.update();
                p.draw();
            });
            requestAnimationFrame(animateParticles);
        }
        animateParticles();
    }

    // 3. Theme Switcher
    const toggleButton = document.getElementById("themeToggle");
    const htmlElement = document.documentElement;
    const savedTheme = localStorage.getItem("theme");
    const systemPrefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;

    if (savedTheme === "dark" || (!savedTheme && systemPrefersDark)) {
        htmlElement.setAttribute("data-theme", "dark");
    } else {
        htmlElement.setAttribute("data-theme", "light");
    }

    if (toggleButton) {
        toggleButton.addEventListener("click", () => {
            const currentTheme = htmlElement.getAttribute("data-theme");
            const newTheme = currentTheme === "dark" ? "light" : "dark";
            htmlElement.setAttribute("data-theme", newTheme);
            localStorage.setItem("theme", newTheme);
        });
    }

    // 4. Scroll Animation Observer
    const observerOptions = { root: null, rootMargin: "0px", threshold: 0.15 };
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                entry.target.classList.add("is-visible");
            }
        });
    }, observerOptions);

    document.querySelectorAll(".fade-in-section, .slide-in-right").forEach((el) => {
        observer.observe(el);
    });

    // 5. Set Dynamic Year
    const yearSpan = document.getElementById("year");
    if (yearSpan) {
        yearSpan.textContent = new Date().getFullYear();
    }

    // 6. Load License Dynamically
    const licenseText = document.getElementById("licenseText");
    if (licenseText) {
        fetch("https://raw.githubusercontent.com/oliver-lebaigue-bright-bench/glyph-syncronator/main/LICENSE")
            .then(response => {
                if (!response.ok) throw new Error("Failed to load license");
                return response.text();
            })
            .then(text => {
                licenseText.textContent = text;
            })
            .catch(error => {
                licenseText.textContent = "Error loading license. Please visit the GitHub repository to view it.";
                console.error(error);
            });
    }
});

// ==========================================
// MODAL & TEAM DATA
// ==========================================

const teamMembers = {
    oliver: {
        name: "Oliver Lebaigue",
        role: "Owner & Lead Developer",
        image: "https://github.com/oliver-lebaigue-bright-bench.png",
        bio: "Owner, coordinator, and lead developer of glyph-syncronator. Focused on high-fidelity synchronization, modern UI, and core visualization logic.",
        socials: [
            { platform: "GitHub", url: "https://github.com/oliver-lebaigue-bright-bench" }
        ]
    },
    rkyzen: {
        name: "rKyzen",
        role: "Core Logic Developer",
        image: "https://github.com/rKyzen.png",
        bio: "Implemented the real-time music streaming foundations and the first versions of the Android app, ensuring high performance.",
        socials: [
            { platform: "GitHub", url: "https://github.com/rKyzen" }
        ]
    },
    cookie: {
        name: "Cookie",
        role: "Glyph Specialist",
        image: "https://github.com/cookiedcdev.png",
        bio: "Creator of the Phone (3) matrix presets and handles community outreach for the project.",
        socials: [
            { platform: "GitHub", url: "https://github.com/cookiedcdev" }
        ]
    }
};

const modal = document.getElementById("teamModal");

function openModal(memberId) {
    const member = teamMembers[memberId];
    if (!member) return;

    document.getElementById("modalImage").src = member.image;
    document.getElementById("modalName").textContent = member.name;
    document.getElementById("modalRole").textContent = member.role;
    document.getElementById("modalBio").textContent = member.bio;

    const modalSocials = document.getElementById("modalSocials");
    modalSocials.innerHTML = "";
    member.socials.forEach(social => {
        const a = document.createElement("a");
        a.href = social.url;
        a.target = "_blank";
        a.className = "social-link";
        a.textContent = social.platform;
        modalSocials.appendChild(a);
    });

    modal.classList.add("active");
    document.body.style.overflow = "hidden";
}

function closeModal() {
    modal.classList.remove("active");
    document.body.style.overflow = "";
}

window.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
});

document.querySelectorAll(".team-member").forEach(card => {
    card.addEventListener("click", (e) => {
        if (e.target.closest("a")) return;
        const memberId = card.getAttribute("data-member");
        openModal(memberId);
    });
});
