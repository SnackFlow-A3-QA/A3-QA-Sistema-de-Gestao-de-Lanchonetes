package br.com.snackflow.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mapeia a tabela clientes. O CPF fica sempre só com dígitos (11) ou nulo. */
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 11)
    private String cpf;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Cliente() {
        // exigido pelo JPA
    }

    public Cliente(String nome, String cpf, String telefone) {
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
    }

    public Integer getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public boolean isAtivo() {
        return ativo;
    }

    /** Substitui os dados cadastrais já normalizados e validados pelo Service. */
    public void atualizar(String nome, String cpf, String telefone) {
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
    }

    /** Inativação lógica: não há exclusão física de clientes. */
    public void inativar() {
        this.ativo = false;
    }
}
